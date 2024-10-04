/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.opensearch;

import static io.camunda.operate.entities.OperationState.COMPLETED;
import static io.camunda.operate.entities.OperationState.SENT;
import static io.camunda.operate.entities.OperationType.DELETE_PROCESS_INSTANCE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchRetryOperation.UPDATE_RETRY_COUNT;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.*;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate.*;
import static io.camunda.webapps.schema.entities.operate.IncidentState.ACTIVE;
import static java.util.stream.Collectors.toMap;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.conditions.OpensearchCondition;
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
import io.camunda.webapps.schema.descriptors.operate.template.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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

@Conditional(OpensearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class OpensearchIncidentPostImportAction extends AbstractIncidentPostImportAction
    implements PostImportAction {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchIncidentPostImportAction.class);

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  public OpensearchIncidentPostImportAction(final int partitionId) {
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

    record Result(Long key, Long position, String intent) {}
    // query post importer queue
    Query partitionQuery = term(PARTITION_ID, partitionId);
    // first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQuery = stringTerms(PARTITION_ID, List.of("0", String.valueOf(partitionId)));
    }
    final var postImporterQueueRequest =
        searchRequestBuilder(postImporterQueueTemplate)
            .query(
                and(
                    gt(POSITION, lastProcessedPosition == null ? 0 : lastProcessedPosition),
                    term(ACTION_TYPE, PostImporterActionType.INCIDENT.name()),
                    partitionQuery))
            .source(sourceInclude(KEY, POSITION, INTENT))
            .sort(sortOptions(POSITION, SortOrder.Asc))
            .size(operateProperties.getZeebeOpensearch().getBatchSize());

    final var response = richOpenSearchClient.doc().search(postImporterQueueRequest, Result.class);
    incidents2Process =
        response.hits().hits().stream()
            .map(Hit::source)
            .collect(
                toMap(
                    r -> r.key(),
                    r -> IncidentState.createFrom(r.intent()),
                    // when both CREATED adn RESOLVED are present, we overwrite CREATED with
                    // RESOLVED as we can at once resolve the incident
                    (existing, replacement) -> replacement));

    pendingIncidentsBatch.setNewIncidentStates(incidents2Process);
    if (!incidents2Process.isEmpty()) {
      pendingIncidentsBatch.setLastProcessedPosition(
          response.hits().hits().get(response.hits().hits().size() - 1).source().position());
    }
    if (incidents2Process.size() == 0) {
      return pendingIncidentsBatch;
    }

    // collect additional data
    // find incident indices for the case when some of them already archived
    final var incidentSearchRequest =
        searchRequestBuilder(incidentTemplate)
            .query(ids(incidents2Process.keySet().stream().map(String::valueOf).toList()))
            .sort(sortOptions(KEY, SortOrder.Asc))
            .size(operateProperties.getZeebeOpensearch().getBatchSize());

    final var incidentsResponse =
        richOpenSearchClient.doc().search(incidentSearchRequest, IncidentEntity.class);
    final var incidents =
        incidentsResponse.hits().hits().stream()
            .map(
                hit -> {
                  final var incident = hit.source();
                  data.getIncidentIndices().put(hit.id(), hit.index());
                  return incident;
                })
            .toList();
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
    pendingIncidentsBatch.setIncidents(
        new ArrayList<>(incidents)); // Batch incidents must be a mutable list
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
    final var listviewRequest =
        searchRequestBuilder(listViewTemplate)
            .query(
                and(
                    ids(
                        incidents.stream()
                            .map(i -> String.valueOf(i.getFlowNodeInstanceKey()))
                            .toList()),
                    term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION)))
            .source(sc -> sc.fetch(false));
    richOpenSearchClient
        .doc()
        .scrollWith(
            listviewRequest,
            Void.class,
            hits ->
                hits.forEach(
                    hit ->
                        CollectionUtil.addToMap(
                            data.getFlowNodeInstanceInListViewIndices(), hit.id(), hit.index())));
  }

  @Override
  protected boolean processIncidents(final AdditionalData data, final PendingIncidentsBatch batch)
      throws PersistenceException {

    final OpensearchPostImporterRequests updateRequests = new OpensearchPostImporterRequests();

    final List<String> treePathTerms =
        data.getIncidentTreePaths().values().stream()
            .map(this::getTreePathTerms)
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
      return updateRequests.execute(richOpenSearchClient, operateProperties);
    }
    return false;
  }

  private List<String> getTreePathTerms(final String treePath) {
    final var analyzeRequest =
        AnalyzeRequest.of(
            ar ->
                ar.field(ListViewTemplate.TREE_PATH)
                    .index(listViewTemplate.getFullQualifiedName())
                    .text(treePath));
    try {
      final var analyzeResponse = richOpenSearchClient.index().analyze(analyzeRequest);
      return analyzeResponse.tokens().stream()
          .map(AnalyzeToken::token)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when requesting term vectors for tree_path");
    }
  }

  private void getTreePathsWithIncidents(
      final List<String> treePathTerms, final AdditionalData data) {
    record Result(String treePath) {}
    final var searchRequest =
        searchRequestBuilder(incidentTemplate)
            .query(and(stringTerms(TREE_PATH, treePathTerms), term(STATE, ACTIVE.name())))
            .source(sourceInclude(TREE_PATH));
    richOpenSearchClient
        .doc()
        .scrollWith(
            searchRequest,
            Result.class,
            hits -> {
              hits.forEach(
                  hit -> {
                    new TreePath(hit.source().treePath)
                        .extractProcessInstanceIds()
                        .forEach(piId -> data.addPiIdsWithIncidentIds(piId, hit.id()));
                    new TreePath(hit.source().treePath)
                        .extractFlowNodeInstanceIds()
                        .forEach(fniId -> data.addFniIdsWithIncidentIds(fniId, hit.id()));
                  });
            });
  }

  private void updateProcessInstancesState(
      final String incidentId,
      final IncidentState newState,
      final List<String> piIds,
      final AdditionalData data,
      final OpensearchPostImporterRequests requests) {

    if (!data.getProcessInstanceIndices().keySet().containsAll(piIds)) {
      data.getProcessInstanceIndices().putAll(getIndexNamesForIds(listViewTemplate, piIds));
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

  private Map<String, String> getIndexNamesForIds(
      final AbstractTemplateDescriptor template, final Collection<java.lang.String> ids) {
    final Map<String, String> indexNames = new HashMap<>();

    final var request =
        searchRequestBuilder(template).query(ids(ids)).source(sc -> sc.fetch(false));

    richOpenSearchClient
        .doc()
        .scrollWith(
            request,
            Void.class,
            hits -> {
              indexNames.putAll(hits.stream().collect(Collectors.toMap(Hit::id, Hit::index)));
            });
    return indexNames;
  }

  private void updateProcessInstance(
      final Map<String, String> processInstanceIndices,
      final OpensearchPostImporterRequests requests,
      final Map<String, Object> updateFields,
      final String piId) {
    final String index = processInstanceIndices.get(piId);
    createUpdateRequestFor(index, piId, updateFields, null, piId, requests.getListViewRequests());
  }

  private void updateFlowNodeInstancesState(
      final IncidentEntity incident,
      final List<String> fniIds,
      final AdditionalData data,
      final OpensearchPostImporterRequests requests) {

    if (!data.getFlowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceIndices()
          .putAll(getIndexNamesAsList(flowNodeInstanceTemplate, fniIds));
    }

    if (!data.getFlowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      data.getFlowNodeInstanceInListViewIndices()
          .putAll(getIndexNamesAsList(listViewTemplate, fniIds));
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

  private Map<String, List<String>> getIndexNamesAsList(
      final AbstractTemplateDescriptor template, final Collection<String> ids) {
    final Map<String, List<String>> indexNames = new ConcurrentHashMap<>();
    final var request =
        searchRequestBuilder(template).query(ids(ids)).source(sc -> sc.fetch(false));
    richOpenSearchClient
        .doc()
        .scrollWith(
            request,
            Void.class,
            hits -> {
              hits.stream()
                  .collect(
                      Collectors.groupingBy(
                          Hit::id, Collectors.mapping(Hit::index, Collectors.toList())))
                  .forEach(
                      (key, value) ->
                          indexNames.merge(
                              key,
                              value,
                              (v1, v2) -> {
                                v1.addAll(v2);
                                return v1;
                              }));
            });
    return indexNames;
  }

  private void updateFlowNodeInstance(
      final IncidentEntity incident,
      final Map<String, List<String>> flowNodeInstanceIndices,
      final Map<String, List<String>> flowNodeInstanceInListViewIndices,
      final OpensearchPostImporterRequests requests,
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
      final OpensearchPostImporterRequests requests) {
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
    record Result(String treePath) {}
    final var request =
        searchRequestBuilder(listViewTemplate)
            .query(
                ids(
                    incidents.stream()
                        .map(i -> String.valueOf(i.getProcessInstanceKey()))
                        .toList()))
            .source(sourceInclude(ListViewTemplate.TREE_PATH));
    richOpenSearchClient
        .doc()
        .scrollWith(
            request,
            Result.class,
            hits -> {
              data.getProcessInstanceTreePaths()
                  .putAll(
                      hits.stream()
                          .collect(
                              toMap(
                                  hit -> Long.valueOf(hit.id()),
                                  hit -> hit.source().treePath,
                                  (path1, path2) -> path1)));
              data.getProcessInstanceIndices()
                  .putAll(
                      hits.stream()
                          .collect(toMap(Hit::id, hit -> hit.index(), (index1, index2) -> index1)));
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
                    "Process instance is not yet imported for incident processing. Incident id: %s, process instance id: %s. Ignoring.",
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
    final var request =
        searchRequestBuilder(operationTemplate)
            .query(
                and(
                    term(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
                    term(OperationTemplate.TYPE, DELETE_PROCESS_INSTANCE.name()),
                    stringTerms(OperationTemplate.STATE, List.of(SENT.name(), COMPLETED.name()))))
            .size(0);
    return richOpenSearchClient.doc().search(request, Void.class).hits().total().value() > 0;
  }

  private void createUpdateRequestFor(
      final String index,
      final String id,
      @Nullable final Map<String, Object> doc,
      @Nullable final Script script,
      final String routing,
      final Map<String, UpdateOperation> requestMap) {
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
    requestMap.put(
        id,
        UpdateOperation.of(
            u -> {
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
            }));
  }
}
