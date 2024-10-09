/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.elasticsearch;

import static io.camunda.operate.entities.OperationState.COMPLETED;
import static io.camunda.operate.entities.OperationState.SENT;
import static io.camunda.operate.entities.OperationType.DELETE_PROCESS_INSTANCE;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.*;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate.*;
import static io.camunda.webapps.schema.entities.operate.IncidentState.ACTIVE;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.*;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.zeebeimport.post.*;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchIncidentPostImportAction extends AbstractIncidentPostImportAction
    implements PostImportAction {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchIncidentPostImportAction.class);

  @Autowired private RestHighLevelClient esClient;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  public ElasticsearchIncidentPostImportAction(final int partitionId) {
    super(partitionId);
  }

  /**
   * Returns map incident key -> intent (CRAETED|RESOLVED)
   *
   * @param data
   * @param lastProcessedPosition
   * @return
   */
  @Override
  protected PendingIncidentsBatch getPendingIncidents(
      final AdditionalData data, final Long lastProcessedPosition) {

    final PendingIncidentsBatch pendingIncidentsBatch = new PendingIncidentsBatch();

    final Map<Long, IncidentState> incidents2Process;

    // query post importer queue
    QueryBuilder partitionQ = termQuery(PARTITION_ID, partitionId);
    // first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQ = termsQuery(PARTITION_ID, "0", String.valueOf(partitionId));
    }
    final SearchRequest listViewRequest =
        ElasticsearchUtil.createSearchRequest(postImporterQueueTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            rangeQuery(POSITION).gt(lastProcessedPosition),
                            termQuery(ACTION_TYPE, PostImporterActionType.INCIDENT),
                            partitionQ))
                    .fetchSource(new String[] {KEY, POSITION, INTENT}, null)
                    .sort(POSITION)
                    .size(operateProperties.getZeebeElasticsearch().getBatchSize()));
    try {
      final SearchResponse response = esClient.search(listViewRequest, RequestOptions.DEFAULT);
      incidents2Process =
          Arrays.stream(response.getHits().getHits())
              .map(sh -> sh.getSourceAsMap())
              .collect(
                  toMap(
                      fieldsMap -> (Long) fieldsMap.get(KEY),
                      fieldsMap -> IncidentState.createFrom((String) fieldsMap.get(INTENT)),
                      // when both CREATED adn RESOLVED are present, we overwrite CREATED with
                      // RESOLVED as we can at once resolve the incident
                      (existing, replacement) -> replacement));
      pendingIncidentsBatch.setNewIncidentStates(incidents2Process);
      if (incidents2Process.size() > 0) {
        pendingIncidentsBatch.setLastProcessedPosition(
            response
                .getHits()
                .getAt(response.getHits().getHits().length - 1)
                .getSourceAsMap()
                .get(POSITION));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while processing pending incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    if (LOGGER.isDebugEnabled() && !incidents2Process.isEmpty()) {
      LOGGER.debug("Processing incident ids <-> intents: " + incidents2Process);
    }

    if (incidents2Process.size() == 0) {
      return pendingIncidentsBatch;
    }

    // collect additional data
    // find incident indices for the case when some of them already archived
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        idsQuery()
                            .addIds(
                                incidents2Process.keySet().stream()
                                    .map(String::valueOf)
                                    .toArray(String[]::new)))
                    .sort(KEY)
                    .size(operateProperties.getZeebeElasticsearch().getBatchSize()));
    final SearchResponse response;
    final List<IncidentEntity> incidents;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      incidents =
          mapSearchHits(
              response.getHits().getHits(),
              sh -> {
                final IncidentEntity incident =
                    fromSearchHit(sh.getSourceAsString(), objectMapper, IncidentEntity.class);
                data.getIncidentIndices().put(sh.getId(), sh.getIndex());
                return incident;
              });
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while processing pending incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    if (incidents2Process.size() > incidents.size()) {
      final Set<Long> absentIncidents = new HashSet<>(incidents2Process.keySet());
      absentIncidents.removeAll(
          incidents.stream().map(i -> i.getKey()).collect(Collectors.toSet()));
      if (operateProperties.getImporter().isPostImporterIgnoreMissingData()) {
        LOGGER.warn(
            "Not all incidents are yet imported for post processing: "
                + absentIncidents
                + ". This post processor records will be ignored.");
      } else {
        throw new OperateRuntimeException(
            "Not all incidents are yet imported for post processing: " + absentIncidents);
      }
    }
    pendingIncidentsBatch.setIncidents(incidents);
    return pendingIncidentsBatch;
  }

  @Override
  protected void searchForInstances(final List<IncidentEntity> incidents, final AdditionalData data)
      throws IOException {

    try {
      queryData(incidents, data);
      checkDataAndCollectParentTreePaths(incidents, data, false);
    } catch (final OperateRuntimeException ex) {
      // if it failed once we want to give it a chance and to import more data
      // next failure will fail in case ignoring of missing data is not configured
      sleepFor(5000L);
      queryData(incidents, data);
      checkDataAndCollectParentTreePaths(
          incidents, data, operateProperties.getImporter().isPostImporterIgnoreMissingData());
    }

    // find flow node instances in list view
    final SearchRequest fniInListViewRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            idsQuery()
                                .addIds(
                                    incidents.stream()
                                        .map(i -> String.valueOf(i.getFlowNodeInstanceKey()))
                                        .toArray(String[]::new)),
                            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION)))
                    .fetchSource(false));
    scrollWith(
        fniInListViewRequest,
        esClient,
        sh -> {
          Arrays.stream(sh.getHits())
              .forEach(
                  hit ->
                      CollectionUtil.addToMap(
                          data.getFlowNodeInstanceInListViewIndices(),
                          hit.getId(),
                          hit.getIndex()));
        });
  }

  @Override
  protected boolean processIncidents(final AdditionalData data, final PendingIncidentsBatch batch)
      throws PersistenceException {

    final ElasticsearchPostImporterRequests updateRequests =
        new ElasticsearchPostImporterRequests();

    final List<String> treePathTerms =
        data.getIncidentTreePaths().values().stream()
            .map(s -> getTreePathTerms(s))
            .flatMap(List::stream)
            .collect(Collectors.toList());
    getTreePathsWithIncidents(treePathTerms, data);

    for (final IncidentEntity incident : batch.getIncidents()) {
      if (instanceExists(
          incident.getProcessInstanceKey(), data.getProcessInstanceTreePaths().keySet())) {

        // extract all process instance ids and flow node instance ids from tree path
        final String incidentTreePath = data.getIncidentTreePaths().get(incident.getId());

        final List<String> piIds = new TreePath(incidentTreePath).extractProcessInstanceIds();

        final IncidentState newState = batch.getNewIncidentStates().get(incident.getKey());
        updateProcessInstancesState(incident.getId(), newState, piIds, data, updateRequests);

        incident.setState(newState);

        final List<String> fniIds = new TreePath(incidentTreePath).extractFlowNodeInstanceIds();
        updateFlowNodeInstancesState(incident, fniIds, data, updateRequests);
        updateIncidents(
            incident,
            newState,
            data.getIncidentIndices().get(incident.getId()),
            incidentTreePath,
            updateRequests);

      } else {
        if (!operateProperties.getImporter().isPostImporterIgnoreMissingData()) {
          throw new OperateRuntimeException(
              String.format(
                  "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s",
                  incident.getId(), incident.getProcessInstanceKey()));
        } else {
          LOGGER.warn(
              String.format(
                  "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s. Ignoring.",
                  incident.getId(), incident.getProcessInstanceKey()));
          final String incidentTreePath = data.getIncidentTreePaths().get(incident.getId());
          final IncidentState newState = batch.getNewIncidentStates().get(incident.getKey());
          incident.setState(newState);
          updateIncidents(
              incident,
              newState,
              data.getIncidentIndices().get(incident.getId()),
              incidentTreePath,
              updateRequests);
        }
      }
    }
    if (!updateRequests.isEmpty()) {
      return updateRequests.execute(esClient, operateProperties);
    }
    return false;
  }

  private List<String> getTreePathTerms(final String treePath) {
    final AnalyzeRequest request =
        AnalyzeRequest.withField(
            listViewTemplate.getFullQualifiedName(), ListViewTemplate.TREE_PATH, treePath);
    try {
      final AnalyzeResponse analyzeResponse =
          esClient.indices().analyze(request, RequestOptions.DEFAULT);

      return analyzeResponse.getTokens().stream()
          .map(AnalyzeToken::getTerm)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when requesting term vectors for tree_path");
    }
  }

  private void getTreePathsWithIncidents(
      final List<String> treePathTerms, final AdditionalData data) {

    final BoolQueryBuilder query =
        boolQuery().must(termsQuery(TREE_PATH, treePathTerms)).must(termQuery(STATE, ACTIVE));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(new SearchSourceBuilder().query(query).fetchSource(TREE_PATH, null));

    try {
      scroll(
          searchRequest,
          shs -> {
            Arrays.stream(shs.getHits())
                .forEach(
                    sh -> {
                      final List<String> piIds =
                          new TreePath((String) sh.getSourceAsMap().get(TREE_PATH))
                              .extractProcessInstanceIds();
                      piIds.stream()
                          .forEach(piId -> data.addPiIdsWithIncidentIds(piId, sh.getId()));
                      final List<String> fniIds =
                          new TreePath((String) sh.getSourceAsMap().get(TREE_PATH))
                              .extractFlowNodeInstanceIds();
                      fniIds.stream()
                          .forEach(fniId -> data.addFniIdsWithIncidentIds(fniId, sh.getId()));
                    });
          },
          esClient);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for process instances with active incidents: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void updateProcessInstancesState(
      final String incidentId,
      final IncidentState newState,
      final List<String> piIds,
      final AdditionalData data,
      final ElasticsearchPostImporterRequests requests) {

    if (!data.getProcessInstanceIndices().keySet().containsAll(piIds)) {
      data.getProcessInstanceIndices().putAll(getIndexNames(listViewTemplate, piIds, esClient));
    }

    final Map<String, Object> updateFields = new HashMap<>();
    if (newState.equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);
      for (final String piId : piIds) {
        // add incident id
        data.addPiIdsWithIncidentIds(piId, incidentId);
        // if there were no incidents and now one
        if (data.getPiIdsWithIncidentIds().get(piId).size() == 1) {
          updateProcessInstance(data.getProcessInstanceIndices(), requests, updateFields, piId);
        }
      }
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      // exclude instances with other incidents
      for (final String piId : piIds) {
        // remove incident id
        data.deleteIncidentIdByPiId(piId, incidentId);
        if (data.getPiIdsWithIncidentIds().get(piId) == null
            || data.getPiIdsWithIncidentIds().get(piId).size() == 0) {
          updateProcessInstance(data.getProcessInstanceIndices(), requests, updateFields, piId);
        } // otherwise there are more active incidents
      }
    }
  }

  private void updateProcessInstance(
      final Map<String, String> processInstanceIndices,
      final ElasticsearchPostImporterRequests requests,
      final Map<String, Object> updateFields,
      final String piId) {
    final String index = processInstanceIndices.get(piId);
    createUpdateRequestFor(index, piId, updateFields, null, piId, requests.getListViewRequests());
  }

  private void updateFlowNodeInstancesState(
      final IncidentEntity incident,
      final List<String> fniIds,
      final AdditionalData data,
      final ElasticsearchPostImporterRequests requests) {

    if (!data.getFlowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceIndices()
          .putAll(getIndexNamesAsList(flowNodeInstanceTemplate, fniIds, esClient));
    }

    if (!data.getFlowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceInListViewIndices()
          .putAll(getIndexNamesAsList(listViewTemplate, fniIds, esClient));
    }

    final Map<String, Object> updateFields = new HashMap<>();
    if (incident.getState().equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);

      for (final String fniId : fniIds) {

        // add incident id
        data.addFniIdsWithIncidentIds(fniId, incident.getId());
        // if there were now incidents and now one
        if (data.getFniIdsWithIncidentIds().get(fniId).size() == 1) {
          updateFlowNodeInstance(
              incident,
              data.getFlowNodeInstanceIndices(),
              data.getFlowNodeInstanceInListViewIndices(),
              requests,
              updateFields,
              fniId);
        }
      }
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      // exclude instances with other incidents
      for (final String fniId : fniIds) {

        // remove incident id
        data.deleteIncidentIdByFniId(fniId, incident.getId());
        if (data.getFniIdsWithIncidentIds().get(fniId) == null
            || data.getFniIdsWithIncidentIds().get(fniId).size() == 0) {
          updateFlowNodeInstance(
              incident,
              data.getFlowNodeInstanceIndices(),
              data.getFlowNodeInstanceInListViewIndices(),
              requests,
              updateFields,
              fniId);
        } // otherwise there are more active incidents
      }
    }
  }

  private void updateFlowNodeInstance(
      final IncidentEntity incident,
      final Map<String, List<String>> flowNodeInstanceIndices,
      final Map<String, List<String>> flowNodeInstanceInListViewIndices,
      final ElasticsearchPostImporterRequests requests,
      final Map<String, Object> updateFields,
      final String fniId) {
    if (flowNodeInstanceIndices.get(fniId) == null) {
      throw new OperateRuntimeException(
          String.format("Flow node instance was not yet imported %s", fniId));
    }
    flowNodeInstanceIndices
        .get(fniId)
        .forEach(
            index -> {
              createUpdateRequestFor(
                  index,
                  fniId,
                  updateFields,
                  null,
                  incident.getProcessInstanceKey().toString(),
                  requests.getFlowNodeInstanceRequests());
            });
    if (flowNodeInstanceInListViewIndices.get(fniId) == null) {
      throw new OperateRuntimeException(
          String.format("List view data was not yet imported for flow node instance %s", fniId));
    }
    flowNodeInstanceInListViewIndices
        .get(fniId)
        .forEach(
            index -> {
              createUpdateRequestFor(
                  index,
                  fniId,
                  updateFields,
                  null,
                  incident.getProcessInstanceKey().toString(),
                  requests.getListViewRequests());
            });
  }

  private void updateIncidents(
      final IncidentEntity incident,
      final IncidentState newState,
      final String index,
      final String incidentTreePath,
      final ElasticsearchPostImporterRequests requests) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(STATE, newState);
    if (newState.equals(ACTIVE)) {
      updateFields.put(TREE_PATH, incidentTreePath);
    }
    createUpdateRequestFor(
        index,
        incident.getId(),
        updateFields,
        null,
        incident.getProcessInstanceKey().toString(),
        requests.getIncidentRequests());
  }

  private boolean instanceExists(final Long key, final Set<Long> idSet) {
    if (idSet == null) {
      return false;
    }
    return idSet.contains(key);
  }

  private void queryData(final List<IncidentEntity> incidents, final AdditionalData data)
      throws IOException {
    // find process instances (if they exist) that correspond to given incidents
    final SearchRequest piRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        idsQuery()
                            .addIds(
                                incidents.stream()
                                    .map(i -> String.valueOf(i.getProcessInstanceKey()))
                                    .toArray(String[]::new)))
                    .fetchSource(ListViewTemplate.TREE_PATH, null));
    scrollWith(
        piRequest,
        esClient,
        sh -> {
          data.getProcessInstanceTreePaths()
              .putAll(
                  Arrays.stream(sh.getHits())
                      .collect(
                          toMap(
                              hit -> Long.valueOf(hit.getId()),
                              hit -> (String) hit.getSourceAsMap().get(ListViewTemplate.TREE_PATH),
                              (path1, path2) -> path1)));
          data.getProcessInstanceIndices()
              .putAll(
                  Arrays.stream(sh.getHits())
                      .collect(
                          toMap(
                              hit -> hit.getId(),
                              hit -> hit.getIndex(),
                              (index1, index2) -> index1)));
        });
  }

  private void checkDataAndCollectParentTreePaths(
      final List<IncidentEntity> incidents,
      final AdditionalData data,
      final boolean ignoreMissingData)
      throws IOException {
    int countMissingInstance = 0;
    for (final Iterator<IncidentEntity> iterator = incidents.iterator(); iterator.hasNext(); ) {
      final IncidentEntity i = iterator.next();
      String piTreePath = data.getProcessInstanceTreePaths().get(i.getProcessInstanceKey());
      if (piTreePath == null || piTreePath.isEmpty()) {
        // check whether DELETE_PROCESS_INSTANCE operation exists
        if (processInstanceWasDeleted(i.getProcessInstanceKey())) {
          LOGGER.debug(
              "Process instance with the key {} was deleted. Incident post processing will be skipped for id {}.",
              i.getProcessInstanceKey(),
              i.getId());
          iterator.remove();
          continue;
        } else {
          if (!operateProperties.getImporter().isPostImporterIgnoreMissingData()) {
            throw new OperateRuntimeException(
                String.format(
                    "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s",
                    i.getId(), i.getProcessInstanceKey()));
          } else {
            countMissingInstance++;
            piTreePath =
                new TreePath().startTreePath(String.valueOf(i.getProcessInstanceKey())).toString();
            LOGGER.warn(
                String.format(
                    "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s.",
                    i.getId(), i.getProcessInstanceKey()));
          }
        }
      }
      data.getIncidentTreePaths()
          .put(
              i.getId(),
              new TreePath(piTreePath)
                  .appendFlowNode(i.getFlowNodeId())
                  .appendFlowNodeInstance(String.valueOf(i.getFlowNodeInstanceKey()))
                  .toString());
    }

    if (countMissingInstance > 0 && !ignoreMissingData) {
      throw new OperateRuntimeException(
          String.format(
              "%d process instances are not yet imported for incident post processing. Will bew retried...",
              countMissingInstance));
    } else {
      // ignore
    }
  }

  private boolean processInstanceWasDeleted(final long processInstanceKey) throws IOException {
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(operationTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
                            termQuery(OperationTemplate.TYPE, DELETE_PROCESS_INSTANCE.name()),
                            termsQuery(OperationTemplate.STATE, SENT.name(), COMPLETED.name())))
                    .size(0));
    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    return response.getHits().getTotalHits().value > 0;
  }

  private void createUpdateRequestFor(
      final String index,
      final String id,
      @Nullable final Map<String, Object> doc,
      @Nullable final Script script,
      final String routing,
      final Map<String, UpdateRequest> requestMap) {
    if ((doc == null) == (script == null)) {
      throw new OperateRuntimeException(
          "One and only one of 'doc' or 'script' must be provided for the update request");
    }
    if (index == null) {
      final String reason =
          String.format(
              "Cannot create update request for document with id [%s]: index is null. This suggests possible data loss.",
              id);
      if (operateProperties.getImporter().isPostImporterIgnoreMissingData()) {
        LOGGER.error(reason + " Ignoring document...");
        return;
      } else {
        throw new OperateRuntimeException(
            reason + " Note: PostImporter can be configured to ignore missing data.");
      }
    }
    final UpdateRequest updateRequest =
        new UpdateRequest(index, id).retryOnConflict(UPDATE_RETRY_COUNT);
    if (doc == null) {
      updateRequest.script(script);
    } else {
      updateRequest.doc(doc);
    }
    if (index.contains(ListViewTemplate.INDEX_NAME)) {
      updateRequest.routing(routing);
    }
    requestMap.put(id, updateRequest);
  }
}
