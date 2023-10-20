/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Conditional(OpensearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class OpensearchFillPostImporterQueuePlan implements FillPostImporterQueuePlan {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchFillPostImporterQueuePlan.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private MigrationProperties migrationProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;

  @Override
  public FillPostImporterQueuePlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setIncidentsIndexName(String incidentsIndexName) {
    this.incidentsIndexName = incidentsIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setPostImporterQueueIndexName(String postImporterQueueIndexName) {
    this.postImporterQueueIndexName = postImporterQueueIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    long srcCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (srcCount > 0) {
      logger.info("No migration needed for postImporterQueueIndex, already contains data.");
      return;
    }
    //iterate over flow node instances with pending incidents
    try{
    String incidentKeysFieldName = "incidentKeys";
    var request = searchRequestBuilder(listViewIndexName + "*")
        .query(and(
            term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
            term("pendingIncident", true)))
        .source(sourceInclude(incidentKeysFieldName))
        .sort(sortOptions(incidentKeysFieldName, SortOrder.Asc))
        .size(operateProperties.getOpensearch().getBatchSize());
    richOpenSearchClient.doc().scrollWith(request, Long.class,
        rethrowConsumer( hits -> {
          final List<IncidentEntity> incidents = getIncidentEntities(incidentKeysFieldName, hits);
          var batchRequest = richOpenSearchClient.batch().newBatchRequest();
          int index = 0;
          for (IncidentEntity incident : incidents) {
            index++;
            PostImporterQueueEntity entity = createPostImporterQueueEntity(incident, index);
            batchRequest.add(postImporterQueueIndexName,entity);
          }
          batchRequest.execute();
    }), hitsMetadata -> {
          if (flowNodesWithIncidentsCount == null) {
            flowNodesWithIncidentsCount = hitsMetadata.total().value();
          }
        });
  } catch (Exception e) {
    throw new MigrationException(e.getMessage(), e);
  }
  }

  private List<IncidentEntity> getIncidentEntities(String incidentKeysFieldName, List<Hit<Long>> hits) {
    var incidentKeys = hits.stream().map(h -> h.source()).toList();
    var request = searchRequestBuilder(incidentKeysFieldName + "*")
        .query(longTerms(IncidentTemplate.ID, incidentKeys))
        .sort(sortOptions(IncidentTemplate.ID, SortOrder.Asc))
        .size(operateProperties.getOpensearch().getBatchSize());
    return richOpenSearchClient.doc().searchValues(request, IncidentEntity.class);
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
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    long dstCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (flowNodesWithIncidentsCount != null && flowNodesWithIncidentsCount > dstCount) {
      throw new MigrationException(String.format(
          "Exception occurred when migrating %s. Number of flow nodes with pending incidents: %s, number of documents in post-importer-queue: %s",
          postImporterQueueIndexName, flowNodesWithIncidentsCount,  dstCount));
    }
  }

  @Override
  public String toString() {
    return "OpensearchFillPostImporterQueuePlan{" + "listViewIndexName='" + listViewIndexName + '\'' + ", incidentsIndexName='" + incidentsIndexName + '\'' + ", postImporterQueueIndexName='" + postImporterQueueIndexName + '\'' + ", operateProperties=" + operateProperties + ", migrationProperties=" + migrationProperties + ", objectMapper=" + objectMapper + ", flowNodesWithIncidentsCount=" + flowNodesWithIncidentsCount + '}';
  }
}
