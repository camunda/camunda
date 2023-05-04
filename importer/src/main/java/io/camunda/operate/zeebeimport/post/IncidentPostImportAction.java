/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import static io.camunda.operate.entities.IncidentState.ACTIVE;
import static io.camunda.operate.entities.IncidentState.RESOLVED;
import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.STATE;
import static io.camunda.operate.schema.templates.IncidentTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.ID;
import static io.camunda.operate.schema.templates.TemplateDescriptor.PARTITION_ID;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Collections.EMPTY_LIST;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.zeebeimport.util.TreePath;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.elasticsearch.action.bulk.BulkRequest;
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
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class IncidentPostImportAction implements PostImportAction {

  private static final Logger logger = LoggerFactory.getLogger(IncidentPostImportAction.class);
  public static final long BACKOFF = 2000L;

  private int partitionId;

  @Autowired
  @Qualifier("postImportThreadPoolScheduler")
  private ThreadPoolTaskScheduler postImportScheduler;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ObjectMapper objectMapper;

  public IncidentPostImportAction(final int partitionId) {
    this.partitionId = partitionId;
  }

  private List<IncidentEntity> processPendingIncidents() {

    //we have two bulk requests:
    // 1. update process instances, flow node instances and incident
    // 2. update pendingIncident flag for flow node instance in list-view index
    // in case 1st has failed we won't execute the 2nd -> will be retried with next run
    BulkRequest bulkProcessAndFlowNodeInstanceUpdate = new BulkRequest();
    BulkRequest bulkPendingIncidentUpdate = new BulkRequest();

    Map<String, String> incidentIndices = new ConcurrentHashMap<>();
    Map<String, String> listViewFlowNodeIndices = new ConcurrentHashMap<>();
    List<IncidentEntity> incidents = getPendingIncidents(incidentIndices, listViewFlowNodeIndices);

    if (incidents.isEmpty()) {
      return incidents;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Processing pending incidents: " + incidents);
    }
    try {
      Set<Long> flowNodeInstanceIds = new HashSet<>();
      Map<String, List<String>> flowNodeInstanceIndices = new ConcurrentHashMap<>();
      Map<String, List<String>> flowNodeInstanceInListViewIndices = new ConcurrentHashMap<>();
      Map<Long, String> processInstanceTreePaths = new ConcurrentHashMap<>();
      Map<String, String> processInstanceIndices = new ConcurrentHashMap<>();
      searchForInstances(incidents, flowNodeInstanceIds, flowNodeInstanceIndices,
          flowNodeInstanceInListViewIndices, processInstanceTreePaths, processInstanceIndices);
      for (IncidentEntity incident: incidents) {
        if (instanceExists(incident.getFlowNodeInstanceKey(), flowNodeInstanceIds) &&
            instanceExists(incident.getProcessInstanceKey(), processInstanceTreePaths.keySet())) {

          //extract all process instance ids and flow node instance ids from tree path
          final String treePath = processInstanceTreePaths.get(incident.getProcessInstanceKey());
          String flowNodeInstanceId = String.valueOf(incident.getFlowNodeInstanceKey());
          final String incidentTreePath = new TreePath(treePath)
              .appendFlowNode(incident.getFlowNodeId())
              .appendFlowNodeInstance(flowNodeInstanceId).toString();

          List<String> piIds = new TreePath(treePath).extractProcessInstanceIds();
          Set<String> piIdsWithIncidents = new HashSet<>();
          Set<String> fniIdsWithIncidents = new HashSet<>();

          List<String> treePathsWithIncidents;
          if (incident.getState().equals(RESOLVED)) {
            final List<String> treePathTerms = getTreePathTerms(treePath);
            treePathTerms.add(incidentTreePath);
            treePathsWithIncidents = getTreePathsWithOtherIncidents(treePathTerms,
                incident.getKey());
            piIdsWithIncidents = treePathsWithIncidents.stream()
                .map(tp -> new TreePath(tp).extractProcessInstanceIds()).flatMap(List::stream)
                .collect(Collectors.toSet());
            fniIdsWithIncidents = treePathsWithIncidents.stream()
                .map(tp -> new TreePath(tp).extractFlowNodeInstanceIds()).flatMap(List::stream)
                .collect(Collectors.toSet());
          }

          updateProcessInstancesState(incident.getState(), piIds, processInstanceIndices, piIdsWithIncidents,
              bulkProcessAndFlowNodeInstanceUpdate);
          final List<String> fniIds = new TreePath(incidentTreePath).extractFlowNodeInstanceIds();
          updateFlowNodeInstancesState(incident.getState(), fniIds, flowNodeInstanceIndices,
              flowNodeInstanceInListViewIndices, fniIdsWithIncidents, bulkProcessAndFlowNodeInstanceUpdate);
          updateIncidents(incident.getId(), incidentIndices.get(incident.getId()),
              incident.getState(), incidentTreePath, bulkProcessAndFlowNodeInstanceUpdate);

          updatePendingIncidentState(flowNodeInstanceId, listViewFlowNodeIndices.get(flowNodeInstanceId),
              incident.getId(), bulkPendingIncidentUpdate);
        }
      }

      if (!bulkProcessAndFlowNodeInstanceUpdate.requests().isEmpty()) {
        ElasticsearchUtil.processBulkRequest(esClient, bulkProcessAndFlowNodeInstanceUpdate, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
        ElasticsearchUtil.processBulkRequest(esClient, bulkPendingIncidentUpdate, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
        ThreadUtil.sleepFor(3000L);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Finished processing");
      }
    } catch (IOException | PersistenceException e) {
      final String message = String.format(
          "Exception occurred, while processing pending incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return incidents;
  }

  private List<IncidentEntity> getPendingIncidents(final Map<String, String> incidentIndices,
      final Map<String, String> listViewFlowNodeIndices) {
    //query pending incident keys from list-view index
    QueryBuilder partitionQ = termQuery(PARTITION_ID, partitionId);
    //first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQ = termsQuery(PARTITION_ID, "0", String.valueOf(partitionId));
    }
    final SearchRequest listViewRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate).source(
        new SearchSourceBuilder().query(
                joinWithAnd(
                    termQuery(ListViewTemplate.PENDING_INCIDENT, true),
                    partitionQ
                    )
            )
            .fetchSource(INCIDENT_KEYS, null)
            .sort(ID)
            .size(operateProperties.getZeebeElasticsearch().getBatchSize())
    );
    final List incidentIds = new ArrayList<>();
    try {
      final SearchResponse response = esClient.search(listViewRequest, RequestOptions.DEFAULT);
      mapSearchHits(response.getHits().getHits(), sh -> {
        listViewFlowNodeIndices.put(sh.getId(), sh.getIndex());
        if (sh.getSourceAsMap().get(INCIDENT_KEYS) != null) {
          incidentIds.addAll((List)sh.getSourceAsMap().get(INCIDENT_KEYS));
        }
        return true;
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while processing pending incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    if (logger.isDebugEnabled() && !listViewFlowNodeIndices.isEmpty()) {
      logger.debug("Processing flow node instances: " + listViewFlowNodeIndices.keySet() + " and incidents: " + incidentIds);
    }

    List<IncidentEntity> incidents = new ArrayList<>();
    if (incidentIds.size() == 0) {
      return incidents;
    }
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(incidentTemplate).source(
        new SearchSourceBuilder().query(idsQuery().addIds((String[])incidentIds.stream().map(String::valueOf).toArray(String[]::new)))
            .sort(KEY).size(operateProperties.getZeebeElasticsearch().getBatchSize()));
    final SearchResponse response;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      incidents
          .addAll(mapSearchHits(response.getHits().getHits(),
              sh -> {
                final IncidentEntity incident = fromSearchHit(sh.getSourceAsString(),
                    objectMapper, IncidentEntity.class);
                incidentIndices.put(sh.getId(), sh.getIndex());
                return incident;
              }));
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while processing pending incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return incidents;
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

  private List<String> getTreePathsWithOtherIncidents(final List<String> treePathTerms,
      final long incidentKey) {

    final BoolQueryBuilder query = boolQuery()
        .must(termsQuery(TREE_PATH, treePathTerms))
        .must(termQuery(STATE, ACTIVE))
        .mustNot(termQuery(KEY, incidentKey));

    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(incidentTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(TREE_PATH, null));

    try {
      return scrollFieldToList(searchRequest, TREE_PATH, esClient).stream()
          .map(String::valueOf).distinct().collect(Collectors.toList());
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while searching for process instances with active incidents: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void updateProcessInstancesState(final IncidentState state, final List<String> piIds,
      Map<String, String> processInstanceIndices, final Set<String> piIdsWithIncidents,
      final BulkRequest bulkUpdateRequest) {

    final List<String> piIds2Update = new ArrayList<>(piIds);

    Map<String, Object> updateFields = new HashMap<>();
    if (state.equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      //exclude instances with other incidents
      piIds2Update.removeAll(piIdsWithIncidents);
    }

    if (!processInstanceIndices.keySet().containsAll(piIds)) {
      processInstanceIndices = getIndexNames(listViewTemplate, piIds, esClient);
    }

    for (String piId: piIds2Update) {
      bulkUpdateRequest.add(new UpdateRequest(processInstanceIndices.get(piId), piId)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT));
    }

  }

  private void updateFlowNodeInstancesState(final IncidentState state, final List<String> fniIds,
      Map<String, List<String>> flowNodeInstanceIndices,
      Map<String, List<String>> flowNodeInstanceInListViewIndices,
      final Set<String> fniIdsWithIncidents, final BulkRequest bulkUpdateRequest) {

    final List<String> fniIds2Update = new ArrayList<>(fniIds);

    Map<String, Object> updateFields = new HashMap<>();
    if (state.equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);
    } else {
      updateFields.put(ListViewTemplate.INCIDENT, false);
      //exclude instances with other incidents
      fniIds2Update.removeAll(fniIdsWithIncidents);
    }

    if (!flowNodeInstanceIndices.keySet().containsAll(fniIds)) {
      flowNodeInstanceIndices.putAll(getIndexNamesAsList(flowNodeInstanceTemplate, fniIds, esClient));
    }

    if (!flowNodeInstanceInListViewIndices.keySet().containsAll(fniIds)) {
      flowNodeInstanceInListViewIndices.putAll(getIndexNamesAsList(listViewTemplate, fniIds, esClient));
    }

    for (String fniId : fniIds2Update) {
      flowNodeInstanceIndices.getOrDefault(fniId, List.of()).forEach(index -> {
        bulkUpdateRequest.add(new UpdateRequest(index, fniId)
            .doc(updateFields)
            .retryOnConflict(UPDATE_RETRY_COUNT));
      });
      if (flowNodeInstanceInListViewIndices.get(fniId) == null) {
        throw new OperateRuntimeException(
            String.format(
                "List view data was not yet imported for flow node instance %s",
                fniId));
      } else {
        flowNodeInstanceInListViewIndices.getOrDefault(fniId, List.of()).forEach(index -> {
          bulkUpdateRequest.add(new UpdateRequest(index, fniId)
              .doc(updateFields)
              .retryOnConflict(UPDATE_RETRY_COUNT));
        });
      }
    }
  }

  private void updateIncidents(final String incidentId, final String index,
      final IncidentState state, final String incidentTreePath, final BulkRequest bulkUpdateRequest) {
    if (state.equals(ACTIVE)) {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(IncidentTemplate.PENDING, false);
      updateFields.put(TREE_PATH, incidentTreePath);
      bulkUpdateRequest.add(new UpdateRequest(index, incidentId)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT));
    } else {
      //we don't remove resolved incidents any more
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(IncidentTemplate.PENDING, false);
      bulkUpdateRequest.add(new UpdateRequest(index, incidentId)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT));
    }
  }

  private void updatePendingIncidentState(final String flowNodeInstanceId, final String index, final String incidentId,
      final BulkRequest bulkUpdateRequest) {
    bulkUpdateRequest.add(new UpdateRequest(index, flowNodeInstanceId)
        .script(getUpdatePendingIncidentScript(incidentId))
        .retryOnConflict(UPDATE_RETRY_COUNT));
  }

  private Script getUpdatePendingIncidentScript(final String incidentId) {
    final Map<String, Object> paramsMap = Map.of(
        "incidentId", Long.valueOf(incidentId));
    final String script =
              "if (ctx._source.containsKey(\"" + INCIDENT_KEYS + "\")) {"
            + "  for (int i=ctx._source." + INCIDENT_KEYS + ".length-1; i>=0; i--) {"
            + "     if (ctx._source." + INCIDENT_KEYS + "[i] == params.incidentId) {"
            + "        ctx._source." + INCIDENT_KEYS + ".remove(i);"
            + "     }"
            + "  }"
            + "  if (ctx._source." + INCIDENT_KEYS + ".length == 0) {"
            + "    ctx._source." + PENDING_INCIDENT + " = false;"
            + "  } "
            + "}";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, paramsMap);
  }

  private boolean instanceExists(final Long key, final Set<Long> idSet) {
    return idSet.contains(key);
  }

  private void searchForInstances(final List<IncidentEntity> incidents,
      final Set<Long> flowNodeInstanceIds, final Map<String, List<String>> flowNodeInstanceIndices,
      final Map<String, List<String>> flowNodeInstanceInListViewIndices,
      final Map<Long, String> processInstanceTreePaths, final Map<String, String> processInstanceIndices)
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
      processInstanceTreePaths.putAll(
          Arrays.stream(sh.getHits()).collect(Collectors.toMap(hit -> {
            return Long.valueOf(hit.getId());
          }, hit -> {
            return (String) hit.getSourceAsMap().get(ListViewTemplate.TREE_PATH);
          })));
      processInstanceIndices.putAll(
          Arrays.stream(sh.getHits()).collect(Collectors.toMap(hit -> {
            return hit.getId();
          }, hit -> {
            return hit.getIndex();
          })));
    });

    //find flow node instances (if they exist) that correspond to given incidents
    final SearchRequest fniRequest = ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(idsQuery()
                .addIds(incidents.stream().map(i -> String.valueOf(i.getFlowNodeInstanceKey()))
                    .toArray(String[]::new)))
            .fetchSource(false)
        );
    scrollWith(fniRequest, esClient, sh -> {
      flowNodeInstanceIds
          .addAll(Arrays.stream(sh.getHits()).map(hit -> Long.valueOf(hit.getId()))
              .collect(Collectors.toList()));
      Arrays.stream(sh.getHits()).forEach(hit ->
          CollectionUtil.addToMap(flowNodeInstanceIndices, hit.getId(), hit.getIndex())
      );
    });

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
          CollectionUtil.addToMap(flowNodeInstanceInListViewIndices, hit.getId(), hit.getIndex())
      );
    });
  }

  /**
   *
   * @return true when we need to continue
   */
  @Override
  public boolean performOneRound() {
    List<IncidentEntity> pendingIncidents = processPendingIncidents();
    return  pendingIncidents.size() > 0;
  }

  @Override
  public void run() {
      try {
        if (performOneRound()) {
          postImportScheduler.submit(this);
        } else {
          postImportScheduler.schedule(this, Instant.now().plus(BACKOFF, MILLIS));
        }
      } catch (Exception ex) {
        logger.error(String.format(
            "Exception occurred when performing post import for partition %d: %s. Will be retried...",
            partitionId, ex.getMessage()), ex);
        //TODO can it fail here?
        postImportScheduler.schedule(this, Instant.now().plus(BACKOFF, MILLIS));
      }
  }

}
