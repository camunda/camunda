/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operate.post.PostImporterQueueEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

public class OpensearchFillPostImporterQueuePlan implements FillPostImporterQueuePlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchFillPostImporterQueuePlan.class);

  private final OperateProperties operateProperties;

  private final MigrationProperties migrationProperties;

  private final ObjectMapper objectMapper;

  private final RichOpenSearchClient richOpenSearchClient;

  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;

  public OpensearchFillPostImporterQueuePlan(
      final RichOpenSearchClient richOpenSearchClient,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper,
      final OperateProperties operateProperties,
      final MigrationProperties migrationProperties) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.objectMapper = objectMapper;
    this.operateProperties = operateProperties;
    this.migrationProperties = migrationProperties;
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
    try {
      final String incidentKeysFieldName = "incidentKeys";
      final var request =
          searchRequestBuilder(listViewIndexName + "*")
              .query(
                  and(term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION), term("pendingIncident", true)))
              .source(sourceInclude(incidentKeysFieldName))
              .sort(sortOptions(incidentKeysFieldName, SortOrder.Asc))
              .size(operateProperties.getOpensearch().getBatchSize());
      richOpenSearchClient
          .doc()
          .scrollWith(
              request,
              Long.class,
              rethrowConsumer(
                  hits -> {
                    final List<IncidentEntity> incidents =
                        getIncidentEntities(incidentKeysFieldName, hits);
                    final var batchRequest = richOpenSearchClient.batch().newBatchRequest();
                    int index = 0;
                    for (final IncidentEntity incident : incidents) {
                      index++;
                      final PostImporterQueueEntity entity =
                          createPostImporterQueueEntity(incident, index);
                      batchRequest.add(postImporterQueueIndexName, entity);
                    }
                    batchRequest.execute();
                  }),
              hitsMetadata -> {
                if (flowNodesWithIncidentsCount == null) {
                  flowNodesWithIncidentsCount = hitsMetadata.total().value();
                }
              });
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
      final String incidentKeysFieldName, final List<Hit<Long>> hits) {
    final var incidentKeys = hits.stream().map(Hit::source).toList();
    final var request =
        searchRequestBuilder(incidentKeysFieldName + "*")
            .query(longTerms(IncidentTemplate.ID, incidentKeys))
            .sort(sortOptions(IncidentTemplate.ID, SortOrder.Asc))
            .size(operateProperties.getOpensearch().getBatchSize());
    return richOpenSearchClient.doc().searchValues(request, IncidentEntity.class);
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
    return "OpensearchFillPostImporterQueuePlan{"
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
