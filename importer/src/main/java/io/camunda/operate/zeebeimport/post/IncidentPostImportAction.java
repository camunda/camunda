/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.util.TreePath;
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
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.camunda.operate.entities.IncidentState.ACTIVE;
import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.PostImporterQueueTemplate.*;
import static io.camunda.operate.schema.templates.TemplateDescriptor.PARTITION_ID;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

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
  private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  private ImportPositionEntity lastProcessedPosition;

  public IncidentPostImportAction(final int partitionId) {
    this.partitionId = partitionId;
  }

  private List<IncidentEntity> processPendingIncidents() throws IOException {

    if (lastProcessedPosition == null) {
      lastProcessedPosition = importPositionHolder.getLatestLoadedPosition(
        ImportValueType.INCIDENT.getAliasTemplate(), partitionId);
    }

    AdditionalData data = new AdditionalData();

    PendingIncidentsBatch batch = getPendingIncidents(data, lastProcessedPosition.getPostImporterPosition());

    if (batch.getIncidents().isEmpty()) {
      return new ArrayList<>();
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Processing pending incidents: " + batch.getIncidents());
    }

    try {

      searchForInstances(batch.getIncidents(), data);

      boolean done = processIncidents(data, batch);

      if (batch.getIncidents().size() > 0 && done) {
        lastProcessedPosition.setPostImporterPosition(batch.getLastProcessedPosition());
        importPositionHolder.recordLatestPostImportedPosition(lastProcessedPosition);
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
    return batch.getIncidents();
  }

  private boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch) throws PersistenceException {

    PostImporterRequests updateRequests = new PostImporterRequests();

    if (!data.isTreePathsProcessed()) {
      final List<String> treePathTerms = data.getIncidentTreePaths().values().stream()
          .map(s -> getTreePathTerms(s)).flatMap(List::stream).collect(Collectors.toList());
      getTreePathsWithIncidents(treePathTerms, data);
      data.setTreePathsProcessed(true);
    }

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
  private PendingIncidentsBatch getPendingIncidents(final AdditionalData data, final Long lastProcessedPosition) {

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
        pendingIncidentsBatch.setLastProcessedPosition(Long.valueOf(
            (Integer) response.getHits().getAt(response.getHits().getHits().length - 1).getSourceAsMap()
                .get(POSITION)));
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
      final PostImporterRequests requests) {

    if (!data.getProcessInstanceIndices().keySet().containsAll(piIds)) {
      data.getProcessInstanceIndices().putAll(getIndexNames(listViewTemplate, piIds, esClient));
    }

    Map<String, Object> updateFields = new HashMap<>();
    if (newState.equals(ACTIVE)) {
      updateFields.put(ListViewTemplate.INCIDENT, true);
      for (String piId: piIds) {
        //add incident id
        data.addPiIdsWithIncidentIds(piId, incidentId);
        //if there were now incidents and now one
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
        if (data.getPiIdsWithIncidentIds().get(piId).size() == 0) {
          updateProcessInstance(data.getProcessInstanceIndices(), requests, updateFields, piId);
        } //otherwise there are more active incidents
      }
    }

  }

  private void updateProcessInstance(Map<String, String> processInstanceIndices, PostImporterRequests requests,
      Map<String, Object> updateFields, String piId) {
    String index = processInstanceIndices.get(piId);
    UpdateRequest updateRequest = createUpdateRequestFor(index, piId, updateFields, null, piId);
    requests.getListViewRequests().put(piId, updateRequest);
  }

  private void updateFlowNodeInstancesState(final IncidentEntity incident, final List<String> fniIds,
      final AdditionalData data, final PostImporterRequests requests) {

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
        if (data.getFniIdsWithIncidentIds().get(fniId).size() == 0) {
          updateFlowNodeInstance(incident, data.getFlowNodeInstanceIndices(),
              data.getFlowNodeInstanceInListViewIndices(), requests, updateFields, fniId);
        } //otherwise there are more active incidents

      }
    }
  }

  private void updateFlowNodeInstance(IncidentEntity incident, Map<String, List<String>> flowNodeInstanceIndices,
      Map<String, List<String>> flowNodeInstanceInListViewIndices, PostImporterRequests requests,
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

  private void updateIncidents(final IncidentEntity incident, final IncidentState newState, final String index, final String incidentTreePath, final PostImporterRequests requests) {
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

  private void searchForInstances(final List<IncidentEntity> incidents, final AdditionalData data)
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
    incidents.forEach(i -> data.getIncidentTreePaths().put(i.getId(),
        new TreePath(data.getProcessInstanceTreePaths().get(i.getProcessInstanceKey())).appendFlowNode(
            i.getFlowNodeId()).appendFlowNodeInstance(String.valueOf(i.getFlowNodeInstanceKey())).toString()));

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

  /**
   *
   * @return true when we need to continue
   */
  @Override
  public boolean performOneRound() throws IOException {
    List<IncidentEntity> pendingIncidents = processPendingIncidents();
    boolean smthWasProcessed = pendingIncidents.size() > 0;
    return smthWasProcessed;
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

  @Override
  public void clearCache() {
    lastProcessedPosition = null;
  }

}

class PendingIncidentsBatch {
  private List<IncidentEntity> incidents = new ArrayList<>();
  private Map<Long, IncidentState> newIncidentStates = new HashMap<>();
  private Long lastProcessedPosition;

  public List<IncidentEntity> getIncidents() {
    return incidents;
  }

  public PendingIncidentsBatch setIncidents(List<IncidentEntity> incidents) {
    this.incidents = incidents;
    return this;
  }

  public Map<Long, IncidentState> getNewIncidentStates() {
    return newIncidentStates;
  }

  public PendingIncidentsBatch setNewIncidentStates(Map<Long, IncidentState> newIncidentStates) {
    this.newIncidentStates = newIncidentStates;
    return this;
  }

  public Long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public PendingIncidentsBatch setLastProcessedPosition(Long lastProcessedPosition) {
    this.lastProcessedPosition = lastProcessedPosition;
    return this;
  }
}

class PostImporterRequests {
  private HashMap<String, UpdateRequest> listViewRequests = new HashMap<>();
  private HashMap<String, UpdateRequest> flowNodeInstanceRequests = new HashMap<>();
  private HashMap<String, UpdateRequest> incidentRequests = new HashMap<>();

  public HashMap<String, UpdateRequest> getListViewRequests() {
    return listViewRequests;
  }

  public PostImporterRequests setListViewRequests(HashMap<String, UpdateRequest> listViewRequests) {
    this.listViewRequests = listViewRequests;
    return this;
  }

  public HashMap<String, UpdateRequest> getFlowNodeInstanceRequests() {
    return flowNodeInstanceRequests;
  }

  public PostImporterRequests setFlowNodeInstanceRequests(HashMap<String, UpdateRequest> flowNodeInstanceRequests) {
    this.flowNodeInstanceRequests = flowNodeInstanceRequests;
    return this;
  }

  public HashMap<String, UpdateRequest> getIncidentRequests() {
    return incidentRequests;
  }

  public PostImporterRequests setIncidentRequests(HashMap<String, UpdateRequest> incidentRequests) {
    this.incidentRequests = incidentRequests;
    return this;
  }
  public boolean isEmpty() {
    return listViewRequests.isEmpty() && flowNodeInstanceRequests.isEmpty() && incidentRequests.isEmpty();
  }

  public boolean execute(RestHighLevelClient esClient, OperateProperties operateProperties)
      throws PersistenceException {

    BulkRequest bulkRequest = new BulkRequest();

    listViewRequests.values().stream().forEach(bulkRequest::add);
    flowNodeInstanceRequests.values().stream().forEach(bulkRequest::add);
    incidentRequests.values().stream().forEach(bulkRequest::add);

    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest,
        operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());

    ThreadUtil.sleepFor(3000L);

    return true;
  }
}

class AdditionalData {

  private Map<String, String> incidentIndices = new ConcurrentHashMap<>();
  private Map<String, List<String>> flowNodeInstanceIndices = new ConcurrentHashMap<>();
  private Map<String, List<String>> flowNodeInstanceInListViewIndices = new ConcurrentHashMap<>();
  private boolean treePathsProcessed = false;
  private Map<Long, String> processInstanceTreePaths = new ConcurrentHashMap<>();
  private Map<String, String> incidentTreePaths = new ConcurrentHashMap<>();
  private Map<String, String> processInstanceIndices = new ConcurrentHashMap<>();
  private Map<String, Set<String>> piIdsWithIncidentIds = new ConcurrentHashMap<>();     //piId <-> active incident ids
  private Map<String, Set<String>> fniIdsWithIncidentIds = new ConcurrentHashMap<>();    //flowNodeInstanceId <-> active incident ids

  public Map<String, List<String>> getFlowNodeInstanceIndices() {
    return flowNodeInstanceIndices;
  }

  public AdditionalData setFlowNodeInstanceIndices(Map<String, List<String>> flowNodeInstanceIndices) {
    this.flowNodeInstanceIndices = flowNodeInstanceIndices;
    return this;
  }

  public Map<String, List<String>> getFlowNodeInstanceInListViewIndices() {
    return flowNodeInstanceInListViewIndices;
  }

  public AdditionalData setFlowNodeInstanceInListViewIndices(
      Map<String, List<String>> flowNodeInstanceInListViewIndices) {
    this.flowNodeInstanceInListViewIndices = flowNodeInstanceInListViewIndices;
    return this;
  }

  public Map<Long, String> getProcessInstanceTreePaths() {
    return processInstanceTreePaths;
  }

  public AdditionalData setProcessInstanceTreePaths(Map<Long, String> processInstanceTreePaths) {
    this.processInstanceTreePaths = processInstanceTreePaths;
    return this;
  }

  public Map<String, String> getProcessInstanceIndices() {
    return processInstanceIndices;
  }

  public AdditionalData setProcessInstanceIndices(Map<String, String> processInstanceIndices) {
    this.processInstanceIndices = processInstanceIndices;
    return this;
  }

  public Map<String, String> getIncidentIndices() {
    return incidentIndices;
  }

  public AdditionalData setIncidentIndices(Map<String, String> incidentIndices) {
    this.incidentIndices = incidentIndices;
    return this;
  }

  public Map<String, Set<String>> getPiIdsWithIncidentIds() {
    return piIdsWithIncidentIds;
  }

  public void addPiIdsWithIncidentIds(String piId, String incidentId) {
    if (piIdsWithIncidentIds.get(piId) == null) {
      piIdsWithIncidentIds.put(piId, new HashSet<>());
    }
    piIdsWithIncidentIds.get(piId).add(incidentId);
  }

  public void deleteIncidentIdByPiId(String piId, String incidentId) {
    if (piIdsWithIncidentIds.get(piId) != null) {
      piIdsWithIncidentIds.get(piId).remove(incidentId);
    }
  }

  public AdditionalData setPiIdsWithIncidentIds(Map<String, Set<String>> piIdsWithIncidentIds) {
    this.piIdsWithIncidentIds = piIdsWithIncidentIds;
    return this;
  }

  public Map<String, Set<String>> getFniIdsWithIncidentIds() {
    return fniIdsWithIncidentIds;
  }

  public void addFniIdsWithIncidentIds(String fniId, String incidentId) {
    if (fniIdsWithIncidentIds.get(fniId) == null) {
      fniIdsWithIncidentIds.put(fniId, new HashSet<>());
    }
    fniIdsWithIncidentIds.get(fniId).add(incidentId);
  }

  public void deleteIncidentIdByFniId(String fniId, String incidentId) {
    if (fniIdsWithIncidentIds.get(fniId) != null) {
      fniIdsWithIncidentIds.get(fniId).remove(incidentId);
    }
  }

  public AdditionalData setFniIdsWithIncidentIds(Map<String, Set<String>> fniIdsWithIncidentIds) {
    this.fniIdsWithIncidentIds = fniIdsWithIncidentIds;
    return this;
  }

  public boolean isTreePathsProcessed() {
    return treePathsProcessed;
  }

  public AdditionalData setTreePathsProcessed(boolean treePathsProcessed) {
    this.treePathsProcessed = treePathsProcessed;
    return this;
  }

  public Map<String, String> getIncidentTreePaths() {
    return incidentTreePaths;
  }

  public AdditionalData setIncidentTreePaths(Map<String, String> incidentTreePaths) {
    this.incidentTreePaths = incidentTreePaths;
    return this;
  }
}
