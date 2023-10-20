/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.*;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.zeebeimport.post.*;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.AnalyzeRequest;
import org.elasticsearch.client.indices.AnalyzeResponse;
import org.elasticsearch.client.indices.AnalyzeResponse.AnalyzeToken;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.camunda.operate.entities.IncidentState.ACTIVE;
import static io.camunda.operate.entities.OperationState.COMPLETED;
import static io.camunda.operate.entities.OperationState.SENT;
import static io.camunda.operate.entities.OperationType.DELETE_PROCESS_INSTANCE;
import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.PostImporterQueueTemplate.*;
import static io.camunda.operate.schema.templates.TemplateDescriptor.PARTITION_ID;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Conditional(ElasticsearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchIncidentPostImportAction extends AbstractIncidentPostImportAction implements PostImportAction {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIncidentPostImportAction.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  public ElasticsearchIncidentPostImportAction(final int partitionId) {
      super(partitionId);
  }

  protected boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch) throws PersistenceException {

    ElasticsearchPostImporterRequests updateRequests = new ElasticsearchPostImporterRequests();

    final List<String> treePathTerms = data.getIncidentTreePaths().values().stream().map(s -> getTreePathTerms(s))
        .flatMap(List::stream).collect(Collectors.toList());
    getTreePathsWithIncidents(treePathTerms, data);

    for (IncidentEntity incident : batch.getIncidents()) {
      if (instanceExists(incident.getProcessInstanceKey(),
          data.getProcessInstanceTreePaths().keySet())) {

        //extract all process instance ids and flow node instance ids from tree path
        final String incidentTreePath = data.getIncidentTreePaths().get(incident.getId());

        List<String> piIds = new TreePath(incidentTreePath).extractProcessInstanceIds();

        IncidentState newState = batch.getNewIncidentStates().get(incident.getKey());
        updateProcessInstancesState(incident.getId(), newState, piIds, data, updateRequests);

        incident.setState(newState);

        final List<String> fniIds = new TreePath(incidentTreePath).extractFlowNodeInstanceIds();
        updateFlowNodeInstancesState(incident, fniIds, data, updateRequests);
        updateIncidents(incident, newState, data.getIncidentIndices().get(incident.getId()), incidentTreePath,
            updateRequests);

      } else {
        throw new OperateRuntimeException(String.format(
            "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s",
            incident.getId(), incident.getProcessInstanceKey()));
      }
    }
    if (!updateRequests.isEmpty()) {
      return updateRequests.execute(esClient, operateProperties);
    }
    return false;
  }

  /**
   * Returns map incident key -> intent (CRAETED|RESOLVED)
   * @param data
   * @param lastProcessedPosition
   * @return
   */
  protected PendingIncidentsBatch getPendingIncidents(final AdditionalData data, final Long lastProcessedPosition) {

    PendingIncidentsBatch pendingIncidentsBatch = new PendingIncidentsBatch();

    Map<Long, IncidentState> incidents2Process;

    //query post importer queue
    QueryBuilder partitionQ = termQuery(PARTITION_ID, partitionId);
    //first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQ = termsQuery(PARTITION_ID, "0", String.valueOf(partitionId));
    }
    final SearchRequest listViewRequest = ElasticsearchUtil.createSearchRequest(postImporterQueueTemplate).source(
        new SearchSourceBuilder()
            .query(joinWithAnd(
                rangeQuery(POSITION).gt(lastProcessedPosition),
                termQuery(ACTION_TYPE, PostImporterActionType.INCIDENT),
                partitionQ))
            .fetchSource(new String[] { KEY, POSITION, INTENT }, null)
            .sort(POSITION)
            .size(operateProperties.getZeebeElasticsearch().getBatchSize()));
    try {
      final SearchResponse response = esClient.search(listViewRequest, RequestOptions.DEFAULT);
      incidents2Process = Arrays.stream(response.getHits().getHits()).map(sh -> sh.getSourceAsMap()).collect(
          toMap(fieldsMap -> (Long) fieldsMap.get(KEY),
              fieldsMap -> IncidentState.createFrom((String) fieldsMap.get(INTENT)),
              //when both CREATED adn RESOLVED are present, we overwrite CREATED with RESOLVED as we can at once resolve the incident
              (existing, replacement) -> replacement));
      pendingIncidentsBatch.setNewIncidentStates(incidents2Process);
      if (incidents2Process.size() > 0) {
        pendingIncidentsBatch.setLastProcessedPosition(
            response.getHits().getAt(response.getHits().getHits().length - 1).getSourceAsMap().get(POSITION));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while processing pending incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    if (logger.isDebugEnabled() && !incidents2Process.isEmpty()) {
      logger.debug("Processing incident ids <-> intents: " + incidents2Process);
    }

    if (incidents2Process.size() == 0) {
      return pendingIncidentsBatch;
    }

    //collect additional data
    //find incident indices for the case when some of them already archived
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(incidentTemplate).source(
        new SearchSourceBuilder().query(
                idsQuery().addIds(incidents2Process.keySet().stream().map(String::valueOf).toArray(String[]::new)))
            .sort(KEY).size(operateProperties.getZeebeElasticsearch().getBatchSize()));
    final SearchResponse response;
    List<IncidentEntity> incidents;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      incidents = mapSearchHits(response.getHits().getHits(), sh -> {
        final IncidentEntity incident = fromSearchHit(sh.getSourceAsString(), objectMapper, IncidentEntity.class);
        data.getIncidentIndices().put(sh.getId(), sh.getIndex());
        return incident;
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while processing pending incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    if (incidents2Process.size() > incidents.size()) {
      throw new OperateRuntimeException("Not all incidents are yet imported for post processing: " + incidents2Process);
    }
    pendingIncidentsBatch.setIncidents(incidents);
    return pendingIncidentsBatch;
  }

  private List<String> getTreePathTerms(final String treePath) {
    AnalyzeRequest request = AnalyzeRequest.withField(
        listViewTemplate.getFullQualifiedName(),
        ListViewTemplate.TREE_PATH,
        treePath
    );
    try {
      final AnalyzeResponse analyzeResponse = esClient.indices()
          .analyze(request, RequestOptions.DEFAULT);

      return analyzeResponse.getTokens().stream().map(AnalyzeToken::getTerm)
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when requesting term vectors for tree_path");
    }
  }

  private void getTreePathsWithIncidents(final List<String> treePathTerms, final AdditionalData data) {

    final BoolQueryBuilder query = boolQuery()
        .must(termsQuery(TREE_PATH, treePathTerms))
        .must(termQuery(STATE, ACTIVE));

    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(incidentTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(TREE_PATH, null));

    try {
      scroll(searchRequest, shs -> {
        Arrays.stream(shs.getHits()).forEach(sh -> {
          List<String> piIds = new TreePath((String) sh.getSourceAsMap().get(TREE_PATH)).extractProcessInstanceIds();
          piIds.stream().forEach(piId -> data.addPiIdsWithIncidentIds(piId, sh.getId()));
          List<String> fniIds = new TreePath((String) sh.getSourceAsMap().get(TREE_PATH)).extractFlowNodeInstanceIds();
          fniIds.stream().forEach(fniId -> data.addFniIdsWithIncidentIds(fniId, sh.getId()));
        });
      }, esClient);
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while searching for process instances with active incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void updateProcessInstancesState(final String incidentId, final IncidentState newState, final List<String> piIds,
      final AdditionalData data,
      final ElasticsearchPostImporterRequests requests) {

    if (!data.getProcessInstanceIndices().keySet().containsAll(piIds)) {
      data.getProcessInstanceIndices().putAll(getIndexNames(listViewTemplate, piIds, esClient));
    }

    Map<String, Object> updateFields = new HashMap<>();
    if (newState.equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);
      for (String piId: piIds) {
        //add incident id
        data.addPiIdsWithIncidentIds(piId, incidentId);
        //if there were no incidents and now one
        if (data.getPiIdsWithIncidentIds().get(piId).size() == 1) {
          updateProcessInstance(data.getProcessInstanceIndices(), requests, updateFields, piId);
        }
      }
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      //exclude instances with other incidents
      for (String piId: piIds) {
        //remove incident id
        data.deleteIncidentIdByPiId(piId, incidentId);
        if (data.getPiIdsWithIncidentIds().get(piId) == null || data.getPiIdsWithIncidentIds().get(piId).size() == 0) {
          updateProcessInstance(data.getProcessInstanceIndices(), requests, updateFields, piId);
        } //otherwise there are more active incidents
      }
    }

  }

  private void updateProcessInstance(Map<String, String> processInstanceIndices, ElasticsearchPostImporterRequests requests,
      Map<String, Object> updateFields, String piId) {
    String index = processInstanceIndices.get(piId);
    UpdateRequest updateRequest = createUpdateRequestFor(index, piId, updateFields, null, piId);
    requests.getListViewRequests().put(piId, updateRequest);
  }

  private void updateFlowNodeInstancesState(final IncidentEntity incident, final List<String> fniIds,
      final AdditionalData data, final ElasticsearchPostImporterRequests requests) {

    if (!data.getFlowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceIndices().putAll(getIndexNamesAsList(flowNodeInstanceTemplate, fniIds, esClient));
    }

    if (!data.getFlowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceInListViewIndices().putAll(getIndexNamesAsList(listViewTemplate, fniIds, esClient));
    }

    Map<String, Object> updateFields = new HashMap<>();
    if (incident.getState().equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);

      for (String fniId : fniIds) {

        //add incident id
        data.addFniIdsWithIncidentIds(fniId, incident.getId());
        //if there were now incidents and now one
        if (data.getFniIdsWithIncidentIds().get(fniId).size() == 1) {
          updateFlowNodeInstance(incident, data.getFlowNodeInstanceIndices(),
              data.getFlowNodeInstanceInListViewIndices(), requests, updateFields, fniId);
        }

      }
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      //exclude instances with other incidents
      for (String fniId: fniIds) {

        //remove incident id
        data.deleteIncidentIdByFniId(fniId, incident.getId());
        if (data.getFniIdsWithIncidentIds().get(fniId) == null || data.getFniIdsWithIncidentIds().get(fniId).size() == 0) {
          updateFlowNodeInstance(incident, data.getFlowNodeInstanceIndices(),
              data.getFlowNodeInstanceInListViewIndices(), requests, updateFields, fniId);
        } //otherwise there are more active incidents

      }
    }
  }

  private void updateFlowNodeInstance(IncidentEntity incident, Map<String, List<String>> flowNodeInstanceIndices,
      Map<String, List<String>> flowNodeInstanceInListViewIndices, ElasticsearchPostImporterRequests requests,
      Map<String, Object> updateFields, String fniId) {
    if (flowNodeInstanceIndices.get(fniId) == null) {
      throw new OperateRuntimeException(String.format("Flow node instance was not yet imported %s", fniId));
    }
    flowNodeInstanceIndices.get(fniId).forEach(index -> {
      UpdateRequest updateRequest = createUpdateRequestFor(index, fniId,
          updateFields, null, incident.getProcessInstanceKey().toString());
      requests.getFlowNodeInstanceRequests().put(fniId, updateRequest);
    });
    if (flowNodeInstanceInListViewIndices.get(fniId) == null) {
      throw new OperateRuntimeException(
          String.format("List view data was not yet imported for flow node instance %s", fniId));
    }
    flowNodeInstanceInListViewIndices.get(fniId).forEach(index -> {
      UpdateRequest updateRequest = createUpdateRequestFor(index, fniId, updateFields, null,
          incident.getProcessInstanceKey().toString());
      requests.getListViewRequests().put(fniId, updateRequest);
    });
  }

  private void updateIncidents(final IncidentEntity incident, final IncidentState newState, final String index, final String incidentTreePath, final ElasticsearchPostImporterRequests requests) {
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(STATE, newState);
    if (newState.equals(ACTIVE)) {
      updateFields.put(TREE_PATH, incidentTreePath);
    }
    UpdateRequest updateRequest = createUpdateRequestFor(index, incident.getId(), updateFields, null, incident.getProcessInstanceKey().toString());
    requests.getIncidentRequests().put(incident.getId(), updateRequest);
  }

  private boolean instanceExists(final Long key, final Set<Long> idSet) {
    if (idSet == null) {
      return false;
    }
    return idSet.contains(key);
  }

  protected void searchForInstances(final List<IncidentEntity> incidents, final AdditionalData data)
      throws IOException {
    //find process instances (if they exist) that correspond to given incidents
    final SearchRequest piRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder()
            .query(idsQuery()
                .addIds(incidents.stream().map(i -> String.valueOf(i.getProcessInstanceKey()))
                    .toArray(String[]::new)))
            .fetchSource(ListViewTemplate.TREE_PATH, null)
        );
    scrollWith(piRequest, esClient, sh -> {
      data.getProcessInstanceTreePaths().putAll(Arrays.stream(sh.getHits()).collect(
          toMap(hit -> Long.valueOf(hit.getId()),
              hit -> (String) hit.getSourceAsMap().get(ListViewTemplate.TREE_PATH))));
      data.getProcessInstanceIndices().putAll(
          Arrays.stream(sh.getHits()).collect(toMap(hit -> hit.getId(), hit -> hit.getIndex())));
    });

    for(Iterator<IncidentEntity> iterator = incidents.iterator(); iterator.hasNext();) {
      IncidentEntity i = iterator.next();
      String piTreePath = data.getProcessInstanceTreePaths().get(i.getProcessInstanceKey());
      if (piTreePath == null || piTreePath.isEmpty()) {
        //check whether DELETE_PROCESS_INSTANCE operation exists
        if (processInstanceWasDeleted(i.getProcessInstanceKey())) {
          logger.debug(
              "Process instance with the key {} was deleted. Incident post processing will be skipped for id {}.",
              i.getProcessInstanceKey(), i.getId());
          iterator.remove();
          continue;
        } else {
          throw new OperateRuntimeException(String.format(
              "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s",
              i.getId(), i.getProcessInstanceKey()));
        }
      }
      data.getIncidentTreePaths().put(i.getId(),
          new TreePath(piTreePath).appendFlowNode(
              i.getFlowNodeId()).appendFlowNodeInstance(String.valueOf(i.getFlowNodeInstanceKey())).toString());
    }

    //find flow node instances in list view
    final SearchRequest fniInListViewRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder()
            .query(joinWithAnd(idsQuery()
                .addIds(incidents.stream().map(i -> String.valueOf(i.getFlowNodeInstanceKey()))
                    .toArray(String[]::new)),
                termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION)))
            .fetchSource(false)
        );
    scrollWith(fniInListViewRequest, esClient, sh -> {
      Arrays.stream(sh.getHits()).forEach(hit ->
          CollectionUtil.addToMap(data.getFlowNodeInstanceInListViewIndices(), hit.getId(), hit.getIndex())
      );
    });
  }

  private boolean processInstanceWasDeleted(long processInstanceKey) throws IOException {
    SearchRequest request =  ElasticsearchUtil.createSearchRequest(operationTemplate)
        .source(new SearchSourceBuilder()
            .query(joinWithAnd(
                termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
                termQuery(OperationTemplate.TYPE, DELETE_PROCESS_INSTANCE.name()),
                termsQuery(OperationTemplate.STATE, SENT.name(), COMPLETED.name())))
            .size(0));
    SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    return response.getHits().getTotalHits().value > 0;
  }

  private UpdateRequest createUpdateRequestFor(String index, String id, @Nullable Map<String,Object> doc, @Nullable Script script, String routing) {
    if ((doc == null) == (script == null)) {
      throw new OperateRuntimeException("One and only one of 'doc' or 'script' must be provided for the update request");
    }
    UpdateRequest updateRequest = new UpdateRequest(index, id).retryOnConflict(UPDATE_RETRY_COUNT);
    if (doc == null) {
      updateRequest.script(script);
    } else {
      updateRequest.doc(doc);
    }
    if (index.contains(ListViewTemplate.INDEX_NAME)) {
      updateRequest.routing(routing);
    }
    return updateRequest;
  }
}

