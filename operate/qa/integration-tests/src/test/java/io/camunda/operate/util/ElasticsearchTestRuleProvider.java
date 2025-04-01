/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.xcontent.XContentType;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTestRuleProvider implements SearchTestRuleProvider {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchTestRuleProvider.class);

  // Scroll contexts constants
  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";
  // Path to find search statistics for all indexes
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  @Autowired protected RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected RecordsReaderHolder recordsReaderHolder;
  protected boolean failed = false;
  Map<Class<? extends ExporterEntity>, String> entityToESAliasMap;
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

  @Autowired private ObjectMapper objectMapper;

  private String indexPrefix;

  @Override
  public void failed(final Throwable e, final Description description) {
    failed = true;
  }

  @Override
  public void starting(final Description description) {
    indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
    if (indexPrefix.isBlank()) {
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
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
      final String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient, indexPrefix);
    }
    operateProperties
        .getElasticsearch()
        .setIndexPrefix(OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX);
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
      final RefreshRequest refreshRequest =
          new RefreshRequest(operateProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
  }

  @Override
  public void refreshOperateSearchIndices() {
    try {
      final RefreshRequest refreshRequest =
          new RefreshRequest(operateProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Operate Elasticsearch indices", t);
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
  public void processRecordsWithTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> predicate,
      final Object... arguments) {
    processRecordsAndWaitFor(getRecordsReaders(importValueType), 50, predicate, null, arguments);
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
    try {
      final BulkRequest bulkRequest = new BulkRequest();
      for (final ExporterEntity entity : operateEntities) {
        final String alias = getEntityToAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }
        final IndexRequest indexRequest =
            new IndexRequest(alias)
                .id(entity.getId())
                .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
        if (entity instanceof FlowNodeInstanceForListViewEntity) {
          indexRequest.routing(
              ((FlowNodeInstanceForListViewEntity) entity).getProcessInstanceKey().toString());
        }
        if (entity instanceof VariableForListViewEntity) {
          indexRequest.routing(
              ((VariableForListViewEntity) entity).getProcessInstanceKey().toString());
        }
        bulkRequest.add(indexRequest);
      }
      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequest,
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
    return getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0);
  }

  @Override
  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public boolean indexExists(final String index) throws IOException {
    final var request = new GetIndexRequest(index);
    return esClient.indices().exists(request, RequestOptions.DEFAULT);
  }

  private boolean areIndicesAreCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final GetIndexResponse response =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexPrefix + "*")
                    .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
                RequestOptions.DEFAULT);
    final String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices;
  }

  private int getIntValueForJSON(
      final String path, final String fieldname, final int defaultValue) {
    final Optional<JsonNode> jsonNode = getJsonFor(path);
    if (jsonNode.isPresent()) {
      final JsonNode field = jsonNode.get().findValue(fieldname);
      if (field != null) {
        return field.asInt(defaultValue);
      }
    }
    return defaultValue;
  }

  private Optional<JsonNode> getJsonFor(final String path) {
    try {
      final ObjectMapper objectMapper = new ObjectMapper();
      final Response response =
          esClient.getLowLevelClient().performRequest(new Request("GET", path));
      return Optional.of(objectMapper.readTree(response.getEntity().getContent()));
    } catch (final Exception e) {
      LOGGER.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.", e);
      return Optional.empty();
    }
  }
}
