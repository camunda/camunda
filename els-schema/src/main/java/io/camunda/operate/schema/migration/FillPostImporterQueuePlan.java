/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

/**
 * This migration plan scrolls the srcIndex, get additional
 * data from list-view index and reindex the batch of source data combining data from
 * source index and list-view.
 */
public class FillPostImporterQueuePlan implements Plan {

  private static final Logger logger = LoggerFactory.getLogger(FillPostImporterQueuePlan.class);
  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;
  private OperateProperties operateProperties;
  private MigrationProperties migrationProperties;
  private ObjectMapper objectMapper;
  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  public String getListViewIndexName() {
    return listViewIndexName;
  }

  public FillPostImporterQueuePlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  public String getIncidentsIndexName() {
    return incidentsIndexName;
  }

  public FillPostImporterQueuePlan setIncidentsIndexName(String incidentsIndexName) {
    this.incidentsIndexName = incidentsIndexName;
    return this;
  }

  public String getPostImporterQueueIndexName() {
    return postImporterQueueIndexName;
  }

  public FillPostImporterQueuePlan setPostImporterQueueIndexName(String postImporterQueueIndexName) {
    this.postImporterQueueIndexName = postImporterQueueIndexName;
    return this;
  }

  public OperateProperties getOperateProperties() {
    return operateProperties;
  }

  public FillPostImporterQueuePlan setOperateProperties(OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
    return this;
  }

  public MigrationProperties getMigrationProperties() {
    return migrationProperties;
  }

  public FillPostImporterQueuePlan setMigrationProperties(MigrationProperties migrationProperties) {
    this.migrationProperties = migrationProperties;
    return this;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public FillPostImporterQueuePlan setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  public FillPostImporterQueuePlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {

    long srcCount = retryElasticsearchClient.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (srcCount > 0) {
      logger.info("No migration needed for postImporterQueueIndex, already contains data.");
      return;
    }

    //iterate over flow node instances with pending incidents
    String incidentKeysFieldName = "incidentKeys";
    SearchRequest searchRequest = new SearchRequest(listViewIndexName + "*")
        .source(new SearchSourceBuilder()
            .query(joinWithAnd(
                termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                termQuery("pendingIncident", true)))
            .fetchSource(incidentKeysFieldName, null)
            .sort(ListViewTemplate.ID)
            .size(operateProperties.getElasticsearch().getBatchSize()));

    RestHighLevelClient esClient = retryElasticsearchClient.getEsClient();
    try {
      scroll(searchRequest, rethrowConsumer(hits -> {
        if (flowNodesWithIncidentsCount == null) {
          flowNodesWithIncidentsCount = hits.getTotalHits().value;
        }
        final List<IncidentEntity> incidents = getIncidentEntities(incidentKeysFieldName, esClient, hits);
        BulkRequest bulkRequest = new BulkRequest();
        final int[] index = { 0 };
        for (IncidentEntity incident : incidents) {
          index[0]++;
          PostImporterQueueEntity entity = createPostImporterQueueEntity(incident, index[0]);
          bulkRequest.add(new IndexRequest().index(postImporterQueueIndexName)
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON));
        }
        ElasticsearchUtil.processBulkRequest(esClient, bulkRequest,
            operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
      }), esClient, migrationProperties.getScrollKeepAlive());
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  private List<IncidentEntity> getIncidentEntities(String incidentKeysFieldName, RestHighLevelClient esClient,
      SearchHits hits) throws IOException {
    List<Long> incidentKeys = Arrays.stream(hits.getHits())
        .map(sh -> (List<Long>)sh.getSourceAsMap().get(incidentKeysFieldName))
        .flatMap(List::stream).collect(Collectors.toList());
    SearchRequest incidentSearchRequest = new SearchRequest(incidentsIndexName + "*").source(
        new SearchSourceBuilder().query(termsQuery(IncidentTemplate.ID, incidentKeys)).sort(IncidentTemplate.ID)
            .size(operateProperties.getElasticsearch().getBatchSize()));

    final SearchResponse incidentsResponse = esClient.search(incidentSearchRequest, RequestOptions.DEFAULT);
    final List<IncidentEntity> incidents = ElasticsearchUtil.mapSearchHits(incidentsResponse.getHits().getHits(),
        objectMapper, IncidentEntity.class);
    return incidents;
  }

  private PostImporterQueueEntity createPostImporterQueueEntity(IncidentEntity incident, long index) {
    return new PostImporterQueueEntity()
        .setId(String.format("%s-%s", incident.getId(), incident.getState().getZeebeIntent()))
        .setCreationTime(OffsetDateTime.now())
        .setKey(incident.getKey())
        .setIntent(incident.getState().getZeebeIntent())
        .setPosition(index)
        .setPartitionId(incident.getPartitionId())
        .setActionType(PostImporterActionType.INCIDENT)
        .setProcessInstanceKey(incident.getProcessInstanceKey());
  }

  @Override
  public void validateMigrationResults(final RetryElasticsearchClient retryElasticsearchClient)
      throws MigrationException {
    long dstCount = retryElasticsearchClient.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (flowNodesWithIncidentsCount != null && flowNodesWithIncidentsCount > dstCount) {
      throw new MigrationException(String.format(
          "Exception occurred when migrating %s. Number of flow nodes with pending incidents: %s, number of documents in post-importer-queue: %s",
          postImporterQueueIndexName, flowNodesWithIncidentsCount,  dstCount));
    }
  }

  @Override
  public String toString() {
    return "FillPostImporterQueuePlan{" + "listViewIndexName='" + listViewIndexName + '\'' + ", incidentsIndexName='" + incidentsIndexName + '\'' + ", postImporterQueueIndexName='" + postImporterQueueIndexName + '\'' + ", operateProperties=" + operateProperties + ", migrationProperties=" + migrationProperties + ", objectMapper=" + objectMapper + ", flowNodesWithIncidentsCount=" + flowNodesWithIncidentsCount + '}';
  }
}
