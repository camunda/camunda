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
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.TemplateDescriptor.PARTITION_ID;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.operate.util.ElasticsearchUtil.getIndexNames;
import static io.camunda.operate.util.ElasticsearchUtil.getIndexNamesAsList;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.mapSearchHits;
import static io.camunda.operate.util.ElasticsearchUtil.scrollFieldToList;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static java.time.temporal.ChronoUnit.MILLIS;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
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
    // 1. update process instances and flow node instances
    // 2. update incident itself and set pending to false
    // in case 1st has failed we won't execute the 2nd -> will be retried with next run
    BulkRequest bulkProcessAndFlowNodeInstanceUpdate = new BulkRequest();
    BulkRequest bulkIncidentUpdate = new BulkRequest();

    Map<String, String> incidentIndices = new HashMap<>();
    List<IncidentEntity> incidents = getPendingIncidents(incidentIndices);
    if (logger.isDebugEnabled() && !incidents.isEmpty()) {
      logger.debug("Processing pending incidents: " + incidents);
    }
    try {
      Set<Long> flowNodeInstanceIds = new HashSet<>();
      Map<String, List<String>> flowNodeInstanceIndices = new HashMap<>();
      Map<String, List<String>> flowNodeInstanceInListViewIndices = new HashMap<>();
      Map<Long, String> processInstanceTreePaths = new HashMap<>();
      Map<String, String> processInstanceIndices = new HashMap<>();
      searchForInstances(incidents, flowNodeInstanceIds, flowNodeInstanceIndices,
          flowNodeInstanceInListViewIndices, processInstanceTreePaths, processInstanceIndices);
      for (IncidentEntity incident: incidents) {
        if (instanceExists(incident.getFlowNodeInstanceKey(), flowNodeInstanceIds) &&
            instanceExists(incident.getProcessInstanceKey(), processInstanceTreePaths.keySet())) {

          //extract all process instance ids and flow node instance ids from tree path
          final String treePath = processInstanceTreePaths.get(incident.getProcessInstanceKey());
          final String incidentTreePath = new TreePath(treePath)
              .appendFlowNode(incident.getFlowNodeId())
              .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey())).toString();

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
              incident.getState(), incidentTreePath, bulkIncidentUpdate);
        }
      }

      if (!bulkProcessAndFlowNodeInstanceUpdate.requests().isEmpty()) {
        ElasticsearchUtil.processBulkRequest(esClient, bulkProcessAndFlowNodeInstanceUpdate);
        ElasticsearchUtil.processBulkRequest(esClient, bulkIncidentUpdate);
        ThreadUtil.sleepFor(1000L);
      }
      if (logger.isDebugEnabled() && !incidents.isEmpty()) {
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

  private List<IncidentEntity> getPendingIncidents(final Map<String, String> incidentIndices) {
    QueryBuilder partitionQ = termQuery(PARTITION_ID, partitionId);
    //first partition will also process older data with partitionId = 0
    if (partitionId == 1) {
      partitionQ = termsQuery(PARTITION_ID, "0", String.valueOf(partitionId));
    }
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(incidentTemplate).source(
        new SearchSourceBuilder().query(
            joinWithAnd(
                termQuery(IncidentTemplate.PENDING, true),
                partitionQ
            )
        )
            .sort(IncidentTemplate.KEY)
            .size(operateProperties.getZeebeElasticsearch().getBatchSize())
    );
    List<IncidentEntity> incidents = new ArrayList<>();
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
      flowNodeInstanceIndices = getIndexNamesAsList(flowNodeInstanceTemplate, fniIds, esClient);
    }

    if (!flowNodeInstanceInListViewIndices.keySet().containsAll(fniIds)) {
      flowNodeInstanceInListViewIndices = getIndexNamesAsList(listViewTemplate, fniIds, esClient);
    }

    for (String fniId : fniIds2Update) {
      flowNodeInstanceIndices.get(fniId).forEach(index -> {
        bulkUpdateRequest.add(new UpdateRequest(index, fniId)
            .doc(updateFields)
            .retryOnConflict(UPDATE_RETRY_COUNT));
      });
      flowNodeInstanceInListViewIndices.get(fniId).forEach(index -> {
        bulkUpdateRequest.add(new UpdateRequest(index, fniId)
            .doc(updateFields)
            .retryOnConflict(UPDATE_RETRY_COUNT));
      });
    }
  }

  private void updateIncidents(final String incidentId, final String index,
      final IncidentState state, final String incidentTreePath, final BulkRequest bulkUpdateRequest) {
    if (state.equals(ACTIVE)) {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(IncidentTemplate.PENDING, false);
      updateFields.put(IncidentTemplate.TREE_PATH, incidentTreePath);
      bulkUpdateRequest.add(new UpdateRequest(index, incidentId)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT));
    } else {
      //delete resolved incident
      bulkUpdateRequest.add(new DeleteRequest().index(index)
          .id(incidentId));
    }

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
