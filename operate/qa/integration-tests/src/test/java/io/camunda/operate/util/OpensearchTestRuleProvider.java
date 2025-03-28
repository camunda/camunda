/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.schema.config.SearchEngineConfiguration;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReader;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionRequirementsEntity;
import io.camunda.webapps.schema.entities.operate.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.runner.Description;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchTestRuleProvider implements SearchTestRuleProvider {

  protected static final Logger LOGGER = LoggerFactory.getLogger(OpensearchTestRuleProvider.class);

  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("zeebeOpensearchClient")
  protected OpenSearchClient zeebeOsClient;

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected SearchEngineConfiguration searchEngineConfiguration;
  @Autowired protected RecordsReaderHolder recordsReaderHolder;
  protected boolean failed = false;
  Map<Class<? extends ExporterEntity>, String> entityToAliasMap;
  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;
  @Autowired private DecisionIndex decisionIndex;
  @Autowired private SchemaManager schemaManager;
  @Autowired private IndexPrefixHolder indexPrefixHolder;
  private String indexPrefix;

  @Override
  public void failed(final Throwable e, final Description description) {
    failed = true;
  }

  @Override
  public void starting(final Description description) {
    indexPrefix = operateProperties.getOpensearch().getIndexPrefix();
    if (indexPrefix.isBlank()) {
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
      searchEngineConfiguration.connect().setIndexPrefix(indexPrefix);
    }
    if (operateProperties.getOpensearch().isCreateSchema()) {
      final var schemaExporterHelper = new SchemaWithExporter(indexPrefix, false);
      schemaExporterHelper.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 5, 5 * 60 /*sec*/))
          .describedAs("Opensearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  public void finished(final Description description) {
    final String indexPrefix = operateProperties.getOpensearch().getIndexPrefix();
    TestUtil.removeAllIndices(
        richOpenSearchClient.index(), richOpenSearchClient.template(), indexPrefix);
    operateProperties
        .getOpensearch()
        .setIndexPrefix(OperateOpensearchProperties.DEFAULT_INDEX_PREFIX);
    searchEngineConfiguration
        .connect()
        .setIndexPrefix(OperateOpensearchProperties.DEFAULT_INDEX_PREFIX);
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
    refreshZeebeIndices();
    refreshOperateSearchIndices();
  }

  @Override
  public void refreshZeebeIndices() {
    try {
      zeebeOsClient
          .indices()
          .refresh(r -> r.index(operateProperties.getZeebeOpensearch().getPrefix() + "*"));
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Zeebe Opensearch indices", t);
    }
  }

  @Override
  public void refreshOperateSearchIndices() {
    try {
      richOpenSearchClient
          .index()
          .refresh(operateProperties.getOpensearch().getIndexPrefix() + "*");
      Thread.sleep(3000); // TODO: Find a way to wait for refresh completion
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Operate Opensearch indices", t);
    }
  }

  @Override
  public void processAllRecordsAndWait(
      final Integer maxWaitingRounds,
      final Predicate<Object[]> predicate,
      final Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), maxWaitingRounds, predicate, null, arguments);
  }

  @Override
  public void processAllRecordsAndWait(
      final Predicate<Object[]> predicate, final Object... arguments) {
    processAllRecordsAndWait(50, predicate, arguments);
  }

  @Override
  public void processAllRecordsAndWait(
      final Predicate<Object[]> predicate,
      final Supplier<Object> supplier,
      final Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), 50, predicate, supplier, arguments);
  }

  @Override
  public void processRecordsAndWaitFor(
      final Collection<RecordsReader> readers,
      final Integer maxWaitingRounds,
      final Predicate<Object[]> predicate,
      final Supplier<Object> supplier,
      final Object... arguments) {
    int waitingRound = 0;
    final int maxRounds = maxWaitingRounds;
    boolean found = predicate.test(arguments);
    final long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshSearchIndices();
        refreshOperateSearchIndices();
      } catch (final Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
      refreshOperateSearchIndices();
      found = predicate.test(arguments);
      if (!found) {
        sleepFor(500);
        waitingRound++;
      }
    }
    final long finishedTime = System.currentTimeMillis() - start;

    if (found) {
      LOGGER.debug("Conditions met in round {} ({} ms).", waitingRound, finishedTime);
    } else {
      LOGGER.debug("Conditions not met after {} rounds ({} ms).", waitingRound, finishedTime);
      //      throw new TestPrerequisitesFailedException("Conditions not met.");
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
        areCreated = areIndicesCreated(indexPrefix, minCountOfIndices);
      } catch (final Exception t) {
        LOGGER.error(
            "Opensearch indices (min {}) are not created yet. Waiting {}/{}",
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    LOGGER.debug("Opensearch indices are created after {} checks", checks);
    return areCreated;
  }

  @Override
  public List<RecordsReader> getRecordsReaders(final ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType))
        .collect(Collectors.toList());
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
    final var batchRequest = richOpenSearchClient.batch().newBatchRequest();

    for (final ExporterEntity entity : operateEntities) {
      final String alias = getEntityToAliasMap().get(entity.getClass());
      if (alias == null) {
        throw new RuntimeException("Index not configured for " + entity.getClass().getName());
      }
      if (entity
          instanceof final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity) {
        batchRequest.addWithRouting(
            alias, entity, flowNodeInstanceForListViewEntity.getProcessInstanceKey().toString());
      } else if (entity instanceof final VariableForListViewEntity variableForListViewEntity) {
        batchRequest.addWithRouting(
            alias, entity, variableForListViewEntity.getProcessInstanceKey().toString());
      } else {
        batchRequest.add(alias, entity);
      }
    }
    batchRequest.executeWithRefresh();
  }

  @Override
  public Map<Class<? extends ExporterEntity>, String> getEntityToAliasMap() {
    if (entityToAliasMap == null) {
      entityToAliasMap = new HashMap<>();
      entityToAliasMap.put(ProcessEntity.class, processIndex.getFullQualifiedName());
      entityToAliasMap.put(IncidentEntity.class, incidentTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          ProcessInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          FlowNodeInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          VariableForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToAliasMap.put(VariableEntity.class, variableTemplate.getFullQualifiedName());
      entityToAliasMap.put(OperationEntity.class, operationTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          BatchOperationEntity.class, batchOperationTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          DecisionInstanceEntity.class, decisionInstanceTemplate.getFullQualifiedName());
      entityToAliasMap.put(
          DecisionRequirementsEntity.class, decisionRequirementsIndex.getFullQualifiedName());
      entityToAliasMap.put(DecisionDefinitionEntity.class, decisionIndex.getFullQualifiedName());
    }
    return entityToAliasMap;
  }

  @Override
  public int getOpenScrollcontextSize() {
    try {
      return richOpenSearchClient.cluster().totalOpenContexts();
    } catch (final Exception e) {
      LOGGER.error("Failed to retrieve open contexts from opensearch! Returning 0.", e);
      return 0;
    }
  }

  @Override
  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public boolean indexExists(final String index) {
    return richOpenSearchClient.index().indexExists(index);
  }

  private boolean areIndicesCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final var indexRequestBuilder =
        getIndexRequestBuilder(indexPrefix + "*")
            .ignoreUnavailable(true)
            .allowNoIndices(false)
            .expandWildcards(ExpandWildcard.Open);

    final GetIndexResponse response = richOpenSearchClient.index().get(indexRequestBuilder);

    final var result = response.result();
    return result.size() >= minCountOfIndices;
  }
}
