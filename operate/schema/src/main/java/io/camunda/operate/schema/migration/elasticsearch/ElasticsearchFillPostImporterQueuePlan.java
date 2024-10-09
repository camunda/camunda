/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operate.post.PostImporterQueueEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.beans.factory.annotation.Qualifier;

public class ElasticsearchFillPostImporterQueuePlan implements FillPostImporterQueuePlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchFillPostImporterQueuePlan.class);

  private final OperateProperties operateProperties;
  private final MigrationProperties migrationProperties;
  private final ObjectMapper objectMapper;
  private final RestHighLevelClient esClient;

  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;

  public ElasticsearchFillPostImporterQueuePlan(
      final OperateProperties operateProperties,
      final MigrationProperties migrationProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper,
      final RestHighLevelClient esClient) {
    this.operateProperties = operateProperties;
    this.migrationProperties = migrationProperties;
    this.objectMapper = objectMapper;
    this.esClient = esClient;
  }

  @Override
  public FillPostImporterQueuePlan setListViewIndexName(final String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setIncidentsIndexName(final String incidentsIndexName) {
    this.incidentsIndexName = incidentsIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setPostImporterQueueIndexName(
      final String postImporterQueueIndexName) {
    this.postImporterQueueIndexName = postImporterQueueIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setSteps(final List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    final long srcCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (srcCount > 0) {
      LOGGER.info("No migration needed for postImporterQueueIndex, already contains data.");
      return;
    }

    // iterate over flow node instances with pending incidents
    final String incidentKeysFieldName = "incidentKeys";
    final SearchRequest searchRequest =
        new SearchRequest(listViewIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                            termQuery("pendingIncident", true)))
                    .fetchSource(incidentKeysFieldName, null)
                    .sort(ListViewTemplate.ID)
                    .size(operateProperties.getElasticsearch().getBatchSize()));

    try {
      scroll(
          searchRequest,
          rethrowConsumer(
              hits -> {
                if (flowNodesWithIncidentsCount == null) {
                  flowNodesWithIncidentsCount = hits.getTotalHits().value;
                }
                final List<IncidentEntity> incidents =
                    getIncidentEntities(incidentKeysFieldName, esClient, hits);
                final BulkRequest bulkRequest = new BulkRequest();
                final int[] index = {0};
                for (final IncidentEntity incident : incidents) {
                  index[0]++;
                  final PostImporterQueueEntity entity =
                      createPostImporterQueueEntity(incident, index[0]);
                  bulkRequest.add(
                      new IndexRequest()
                          .index(postImporterQueueIndexName)
                          .source(objectMapper.writeValueAsString(entity), XContentType.JSON));
                }
                ElasticsearchUtil.processBulkRequest(
                    esClient,
                    bulkRequest,
                    operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
              }),
          esClient,
          migrationProperties.getScrollKeepAlive());
    } catch (final Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    final long dstCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (flowNodesWithIncidentsCount != null && flowNodesWithIncidentsCount > dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of flow nodes with pending incidents: %s, number of documents in post-importer-queue: %s",
              postImporterQueueIndexName, flowNodesWithIncidentsCount, dstCount));
    }
  }

  private List<IncidentEntity> getIncidentEntities(
      final String incidentKeysFieldName, final RestHighLevelClient esClient, final SearchHits hits)
      throws IOException {
    final List<Long> incidentKeys =
        Arrays.stream(hits.getHits())
            .map(sh -> (List<Long>) sh.getSourceAsMap().get(incidentKeysFieldName))
            .flatMap(List::stream)
            .collect(Collectors.toList());
    final SearchRequest incidentSearchRequest =
        new SearchRequest(incidentsIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(IncidentTemplate.ID, incidentKeys))
                    .sort(IncidentTemplate.ID)
                    .size(operateProperties.getElasticsearch().getBatchSize()));

    final SearchResponse incidentsResponse =
        esClient.search(incidentSearchRequest, RequestOptions.DEFAULT);
    final List<IncidentEntity> incidents =
        ElasticsearchUtil.mapSearchHits(
            incidentsResponse.getHits().getHits(), objectMapper, IncidentEntity.class);
    return incidents;
  }

  private PostImporterQueueEntity createPostImporterQueueEntity(
      final IncidentEntity incident, final long index) {
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
  public String toString() {
    return "ElasticsearchFillPostImporterQueuePlan{"
        + "listViewIndexName='"
        + listViewIndexName
        + '\''
        + ", incidentsIndexName='"
        + incidentsIndexName
        + '\''
        + ", postImporterQueueIndexName='"
        + postImporterQueueIndexName
        + '\''
        + ", operateProperties="
        + operateProperties
        + ", migrationProperties="
        + migrationProperties
        + ", objectMapper="
        + objectMapper
        + ", flowNodesWithIncidentsCount="
        + flowNodesWithIncidentsCount
        + '}';
  }
}
