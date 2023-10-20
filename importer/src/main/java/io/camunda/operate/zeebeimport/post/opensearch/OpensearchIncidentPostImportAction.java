/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.*;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.zeebeimport.post.AbstractIncidentPostImportAction;
import io.camunda.operate.zeebeimport.post.AdditionalData;
import io.camunda.operate.zeebeimport.post.PendingIncidentsBatch;
import io.camunda.operate.zeebeimport.post.PostImportAction;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.AnalyzeRequest;
import org.opensearch.client.opensearch.indices.analyze.AnalyzeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchRetryOperation.UPDATE_RETRY_COUNT;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static java.util.stream.Collectors.toMap;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Conditional(OpensearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class OpensearchIncidentPostImportAction extends AbstractIncidentPostImportAction implements PostImportAction {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchIncidentPostImportAction.class);

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

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

  public OpensearchIncidentPostImportAction(final int partitionId) {
      super(partitionId);
  }

  protected boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch) throws PersistenceException {

    OpensearchPostImporterRequests updateRequests = new OpensearchPostImporterRequests();

    final List<String> treePathTerms = data.getIncidentTreePaths().values().stream().map(this::getTreePathTerms)
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
      return updateRequests.execute(richOpenSearchClient, operateProperties);
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

    record Result(Long key,  Long position, String intent){}
    //query post importer queue
    Query partitionQuery = term(PARTITION_ID, partitionId);
    //first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQuery = stringTerms(PARTITION_ID, List.of("0", String.valueOf(partitionId)));
    }
    var postImporterQueueRequest = searchRequestBuilder(postImporterQueueTemplate)
        .query(
            and( gt(POSITION, lastProcessedPosition==null?0:lastProcessedPosition),
                 term(ACTION_TYPE, PostImporterActionType.INCIDENT.name()),
                 partitionQuery))
        .source(sourceInclude(KEY, POSITION, INTENT))
        .sort(sortOptions(POSITION, SortOrder.Asc))
        .size(operateProperties.getZeebeOpensearch().getBatchSize());

    var response = richOpenSearchClient.doc().search(postImporterQueueRequest, Result.class);
    incidents2Process = response.hits().hits()
        .stream()
        .map(Hit::source)
        .collect(
            toMap(
                r -> r.key(),
                r -> IncidentState.createFrom(r.intent()),
                //when both CREATED adn RESOLVED are present, we overwrite CREATED with RESOLVED as we can at once resolve the incident
               (existing, replacement) -> replacement));

      pendingIncidentsBatch.setNewIncidentStates(incidents2Process);
      if (!incidents2Process.isEmpty()) {
        pendingIncidentsBatch.setLastProcessedPosition(
            response.hits().hits().get(response.hits().hits().size() - 1).source().position());
      }
    if (incidents2Process.size() == 0) {
      return pendingIncidentsBatch;
    }

    //collect additional data
    //find incident indices for the case when some of them already archived
    var incidentSearchRequest = searchRequestBuilder(incidentTemplate)
        .query(ids(incidents2Process.keySet().stream().map(String::valueOf).toList()))
        .sort(sortOptions(KEY, SortOrder.Asc))
        .size(operateProperties.getZeebeOpensearch().getBatchSize());

    var incidentsResponse = richOpenSearchClient.doc().search(incidentSearchRequest, IncidentEntity.class);
    var incidents = incidentsResponse.hits().hits().stream().map(hit -> {
      var incident = hit.source();
      data.getIncidentIndices().put(hit.id(), hit.index());
      return incident;
    }).toList();
    if (incidents2Process.size() > incidents.size()) {
      throw new OperateRuntimeException("Not all incidents are yet imported for post processing: " + incidents2Process);
    }
    pendingIncidentsBatch.setIncidents(incidents);
    return pendingIncidentsBatch;
  }

  private List<String> getTreePathTerms(final String treePath) {
    var analyzeRequest = AnalyzeRequest.of( ar -> ar
        .field(ListViewTemplate.TREE_PATH)
        .index(listViewTemplate.getFullQualifiedName())
        .text(treePath));
    try {
      final var analyzeResponse = richOpenSearchClient.index().analyze(analyzeRequest);
      return analyzeResponse.tokens().stream().map(AnalyzeToken::token).collect(Collectors.toList());
    } catch (IOException e) {
      throw new OperateRuntimeException("Exception occurred when requesting term vectors for tree_path");
    }
  }

  private void getTreePathsWithIncidents(final List<String> treePathTerms, final AdditionalData data) {
    record Result(String treePath){}
    var searchRequest = searchRequestBuilder(incidentTemplate)
        .query(and(
            stringTerms(TREE_PATH, treePathTerms),
            term(STATE, ACTIVE.name())
        ))
        .source(sourceInclude(TREE_PATH));
    richOpenSearchClient.doc().scrollWith(searchRequest,Result.class, hits -> {
     hits.forEach(hit -> {
       new TreePath(hit.source().treePath).extractProcessInstanceIds()
           .forEach(piId ->
               data.addPiIdsWithIncidentIds(piId, hit.id()));
       new TreePath(hit.source().treePath).extractFlowNodeInstanceIds()
           .forEach(fniId ->
               data.addFniIdsWithIncidentIds(fniId, hit.id()));
     });
    });
  }

  private void updateProcessInstancesState(final String incidentId, final IncidentState newState, final List<String> piIds,
      final AdditionalData data,
      final OpensearchPostImporterRequests requests) {

    if (!data.getProcessInstanceIndices().keySet().containsAll(piIds)) {
      data.getProcessInstanceIndices().putAll(getIndexNamesForIds(listViewTemplate, piIds));
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

  private Map<String,String> getIndexNamesForIds(AbstractTemplateDescriptor template, Collection<java.lang.String> ids){
    Map<String,String> indexNames = new HashMap<>();

    var request = searchRequestBuilder(template)
        .query(ids(ids))
        .source(sc -> sc.fetch(false));

    richOpenSearchClient.doc().scrollWith(request,Void.class, hits -> {
      indexNames.putAll(
          hits.stream().collect(
              Collectors.toMap(Hit::id, Hit::index))
      );
    });
    return indexNames;
  }

  private void updateProcessInstance(Map<String, String> processInstanceIndices, OpensearchPostImporterRequests requests,
      Map<String, Object> updateFields, String piId) {
    String index = processInstanceIndices.get(piId);
    UpdateOperation updateRequest = createUpdateRequestFor(index, piId, updateFields, null, piId);
    requests.getListViewRequests().put(piId, updateRequest);
  }

  private void updateFlowNodeInstancesState(final IncidentEntity incident, final List<String> fniIds,
      final AdditionalData data, final OpensearchPostImporterRequests requests) {

    if (!data.getFlowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceIndices().putAll(getIndexNamesAsList(flowNodeInstanceTemplate, fniIds));
    }

    if (!data.getFlowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceInListViewIndices().putAll(getIndexNamesAsList(listViewTemplate, fniIds));
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

  private Map<String, List<String>> getIndexNamesAsList(AbstractTemplateDescriptor template, Collection<String> ids) {
    Map<String, List<String>> indexNames = new ConcurrentHashMap<>();
    var request = searchRequestBuilder(template)
        .query(ids(ids))
        .source(sc -> sc.fetch(false));
    richOpenSearchClient.doc().scrollWith(request, Void.class, hits -> {
      hits.stream().collect(
          Collectors.groupingBy(Hit::id, Collectors.mapping(Hit::index, Collectors.toList())))
          .forEach((key, value) -> indexNames.merge(key, value, (v1, v2) -> {
            v1.addAll(v2);
            return v1;
          }));
    });
    return indexNames;
  }

  private void updateFlowNodeInstance(IncidentEntity incident, Map<String, List<String>> flowNodeInstanceIndices,
      Map<String, List<String>> flowNodeInstanceInListViewIndices, OpensearchPostImporterRequests requests,
      Map<String, Object> updateFields, String fniId) {
    if (flowNodeInstanceIndices.get(fniId) == null) {
      throw new OperateRuntimeException(String.format("Flow node instance was not yet imported %s", fniId));
    }
    flowNodeInstanceIndices.get(fniId).forEach(index -> {
      UpdateOperation updateRequest = createUpdateRequestFor(index, fniId,
          updateFields, null, incident.getProcessInstanceKey().toString());
      requests.getFlowNodeInstanceRequests().put(fniId, updateRequest);
    });
    if (flowNodeInstanceInListViewIndices.get(fniId) == null) {
      throw new OperateRuntimeException(
          String.format("List view data was not yet imported for flow node instance %s", fniId));
    }
    flowNodeInstanceInListViewIndices.get(fniId).forEach(index -> {
      UpdateOperation updateRequest = createUpdateRequestFor(index, fniId, updateFields, null,
          incident.getProcessInstanceKey().toString());
      requests.getListViewRequests().put(fniId, updateRequest);
    });
  }

  private void updateIncidents(final IncidentEntity incident, final IncidentState newState, final String index, final String incidentTreePath, final OpensearchPostImporterRequests requests) {
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(STATE, newState);
    if (newState.equals(ACTIVE)) {
      updateFields.put(TREE_PATH, incidentTreePath);
    }
    UpdateOperation updateRequest = createUpdateRequestFor(index, incident.getId(), updateFields, null, incident.getProcessInstanceKey().toString());
    requests.getIncidentRequests().put(incident.getId(), updateRequest);
  }

  private boolean instanceExists(final Long key, final Set<Long> idSet) {
    if (idSet == null) {
      return false;
    }
    return idSet.contains(key);
  }

  protected void searchForInstances(final List<IncidentEntity> incidents, final AdditionalData data) throws IOException {
    //find process instances (if they exist) that correspond to given incidents
    record Result(String treePath){}
    var request = searchRequestBuilder(listViewTemplate)
        .query(ids(incidents.stream().map(i -> String.valueOf(i.getProcessInstanceKey())).toList()))
        .source(sourceInclude(ListViewTemplate.TREE_PATH));
    richOpenSearchClient.doc().scrollWith(request,Result.class, hits -> {
      data.getProcessInstanceTreePaths().putAll(hits.stream().collect(
          toMap(hit -> Long.valueOf(hit.id()),
                hit -> hit.source().treePath)));
      data.getProcessInstanceIndices().putAll(
          hits.stream().collect(toMap(Hit::id, hit -> hit.index())));
    } );

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
    var listviewRequest = searchRequestBuilder(listViewTemplate)
        .query(
            and(
                ids(incidents.stream().map(i -> String.valueOf(i.getFlowNodeInstanceKey())).toList()),
                term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION)))
        .source( sc -> sc.fetch(false));
    richOpenSearchClient.doc().scrollWith(listviewRequest, Void.class, hits ->
        hits.forEach(hit ->
          CollectionUtil.addToMap(data.getFlowNodeInstanceInListViewIndices(), hit.id(), hit.index())
        )
    );
  }

  private boolean processInstanceWasDeleted(long processInstanceKey) throws IOException {
    var request = searchRequestBuilder(operationTemplate)
        .query(and(
            term(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
            term(OperationTemplate.TYPE, DELETE_PROCESS_INSTANCE.name()),
            stringTerms(OperationTemplate.STATE, List.of(SENT.name(), COMPLETED.name()))
            ))
        .size(0);
   return richOpenSearchClient.doc().search(request, Void.class).hits().total().value() > 0;
  }

  private UpdateOperation createUpdateRequestFor(String index, String id, @Nullable Map<String,Object> doc, @Nullable Script script, String routing) {
    if ((doc == null) == (script == null)) {
      throw new OperateRuntimeException("One and only one of 'doc' or 'script' must be provided for the update request");
    }
    if (index == null) {
        throw new OperateRuntimeException("Update cannot be performed on a null index");
    }
    return UpdateOperation.of( u -> {
      u.index(index).id(id).retryOnConflict(UPDATE_RETRY_COUNT);
      if (doc == null) {
        u.script(script);
      } else {
        u.document(doc);
      }
      if (index.contains(ListViewTemplate.INDEX_NAME)) {
        u.routing(routing);
      }
      return u;
    });
  }
}

