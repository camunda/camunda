/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.property.OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.nodes.Stats;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTestRuleProvider implements SearchTestRuleProvider {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchTestRuleProvider.class);

  @Autowired protected ElasticsearchClient es8Client;

  @Autowired protected OperateProperties operateProperties;
  protected boolean failed = false;
  Map<Class<? extends ExporterEntity>, String> entityToESAliasMap;
  @Autowired private SearchEngineConfiguration searchEngineConfiguration;
  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private ProcessIndex processIndex;

  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;
  @Autowired private DecisionIndex decisionIndex;
  @Autowired private IndexPrefixHolder indexPrefixHolder;

  private String indexPrefix;

  @Override
  public void failed(final Throwable e, final Description description) {
    failed = true;
  }

  @Override
  public void starting(final Description description) {
    indexPrefix = searchEngineConfiguration.connect().getIndexPrefix();
    if (indexPrefix.isBlank()) {
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
      searchEngineConfiguration.connect().setIndexPrefix(indexPrefix);
    }
    if (operateProperties.getElasticsearch().isCreateSchema()) {
      final var schemaExporterHelper = new SchemaWithExporter(indexPrefix, true);
      schemaExporterHelper.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 5, 5 * 60 /*sec*/))
          .describedAs("Elasticsearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  public void finished(final Description description) {
    if (!failed) {
      final String indexPrefix = searchEngineConfiguration.connect().getIndexPrefix();
      TestUtil.removeAllIndices(es8Client, indexPrefix);
    }
    operateProperties.getElasticsearch().setIndexPrefix(DEFAULT_INDEX_PREFIX);
    searchEngineConfiguration.connect().setIndexPrefix(DEFAULT_INDEX_PREFIX);
    assertMaxOpenScrollContexts(15);
  }

  @Override
  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  @Override
  public void refreshSearchIndices() {
    refreshOperateSearchIndices();
  }

  @Override
  public void refreshOperateSearchIndices() {
    try {
      final var refreshRequest =
          new RefreshRequest.Builder()
              .index(searchEngineConfiguration.connect().getIndexPrefix() + "*")
              .build();
      es8Client.indices().refresh(refreshRequest);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Operate Elasticsearch indices", t);
    }
  }

  @Override
  public boolean areIndicesCreatedAfterChecks(
      final String indexPrefix, final int minCountOfIndices, final int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesAreCreated(indexPrefix, minCountOfIndices);
      } catch (final Exception t) {
        LOGGER.error(
            "Elasticsearch indices (min {}) are not created yet. Waiting {}/{}",
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    LOGGER.debug("Elasticsearch indices are created after {} checks", checks);
    return areCreated;
  }

  @Override
  public void persistNew(final ExporterEntity... entitiesToPersist) {
    try {
      persistOperateEntitiesNew(Arrays.asList(entitiesToPersist));
    } catch (final PersistenceException e) {
      LOGGER.error("Unable to persist entities: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
    refreshSearchIndices();
  }

  @Override
  public void persistOperateEntitiesNew(final List<? extends ExporterEntity> operateEntities)
      throws PersistenceException {
    try {
      final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

      for (final ExporterEntity entity : operateEntities) {
        final String alias = getEntityToAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }

        bulkRequestBuilder.operations(
            op ->
                op.index(
                    i ->
                        i.index(alias)
                            .id(entity.getId())
                            .document(entity)
                            .routing(getRoutingKey(entity))));
      }

      ElasticsearchUtil.processBulkRequest(
          es8Client,
          bulkRequestBuilder,
          true,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (final Exception ex) {
      throw new PersistenceException(ex);
    }
  }

  @Override
  public Map<Class<? extends ExporterEntity>, String> getEntityToAliasMap() {
    entityToESAliasMap = new HashMap<>();
    entityToESAliasMap.put(ProcessEntity.class, processIndex.getFullQualifiedName());
    entityToESAliasMap.put(IncidentEntity.class, incidentTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        ProcessInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        FlowNodeInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        VariableForListViewEntity.class, listViewTemplate.getFullQualifiedName());
    entityToESAliasMap.put(VariableEntity.class, variableTemplate.getFullQualifiedName());
    entityToESAliasMap.put(OperationEntity.class, operationTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        BatchOperationEntity.class, batchOperationTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        DecisionInstanceEntity.class, decisionInstanceTemplate.getFullQualifiedName());
    entityToESAliasMap.put(
        DecisionRequirementsEntity.class, decisionRequirementsIndex.getFullQualifiedName());
    entityToESAliasMap.put(DecisionDefinitionEntity.class, decisionIndex.getFullQualifiedName());
    return entityToESAliasMap;
  }

  @Override
  public int getOpenScrollcontextSize() {
    int openContexts = 0;
    try {
      final Set<Map.Entry<String, Stats>> nodesResult =
          es8Client.nodes().stats().nodes().entrySet();
      for (final Map.Entry<String, Stats> entryNodes : nodesResult) {
        openContexts += entryNodes.getValue().indices().search().openContexts().intValue();
      }
      return openContexts;
    } catch (final IOException e) {
      LOGGER.error("Couldn't retrieve node stats from elasticsearch.", e);
      return 0;
    }
  }

  @Override
  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public boolean indexExists(final String index) throws IOException {
    final var request = new ExistsRequest.Builder().index(index).build();
    return es8Client.indices().exists(request).value();
  }

  private String getRoutingKey(final ExporterEntity entity) {
    if (entity instanceof final FlowNodeInstanceForListViewEntity flow) {
      return flow.getProcessInstanceKey().toString();
    } else if (entity instanceof final VariableForListViewEntity var) {
      return var.getProcessInstanceKey().toString();
    }
    return null;
  }

  private boolean areIndicesAreCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final var getIndexRequest =
        new GetIndexRequest.Builder()
            .index(indexPrefix + "*")
            .ignoreUnavailable(true)
            .allowNoIndices(false)
            .expandWildcards(ExpandWildcard.Open)
            .build();
    final var response = es8Client.indices().get(getIndexRequest);
    return response.result() != null && response.result().size() >= minCountOfIndices;
  }
}
