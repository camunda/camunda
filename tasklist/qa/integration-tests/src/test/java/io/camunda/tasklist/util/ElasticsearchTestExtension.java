/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.store.elasticsearch.VariableStoreElasticSearch.MAX_TERMS_COUNT_SETTING;
import static io.camunda.tasklist.util.ElasticsearchUtil.LENIENT_EXPAND_OPEN_FORBID_NO_INDICES_IGNORE_THROTTLED;
import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReader;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(
    name = "camunda.tasklist.database",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class ElasticsearchTestExtension
    implements DatabaseTestExtension,
        BeforeEachCallback,
        AfterEachCallback,
        TestExecutionExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchTestExtension.class);

  /** Scroll contexts constants */
  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";

  /** Path to find search statistics for all indexes */
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private ZeebeImporter zeebeImporter;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  private boolean failed = false;
  @Autowired private SchemaManager elasticsearchSchemaManager;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestImportListener testImportListener;
  private String indexPrefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    if (indexPrefix == null) {
      indexPrefix = TestUtil.createRandomString(10) + "-tasklist";
    }
    tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    if (tasklistProperties.getElasticsearch().isCreateSchema()) {
      elasticsearchSchemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 4, 5 * 60 /*sec*/))
          .describedAs("Elasticsearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    if (!failed) {
      final String indexPrefix = tasklistProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient, indexPrefix);
    }
    tasklistProperties
        .getElasticsearch()
        .setIndexPrefix(TasklistElasticsearchProperties.DEFAULT_INDEX_PREFIX);
    assertMaxOpenScrollContexts(10);
  }

  @Override
  public void setIndexMaxTermsCount(final String indexName, final int maxTermsCount)
      throws IOException {
    esClient
        .indices()
        .putSettings(
            new UpdateSettingsRequest()
                .indices(indexName)
                .settings(Settings.builder().put(MAX_TERMS_COUNT_SETTING, maxTermsCount).build()),
            RequestOptions.DEFAULT);
  }

  @Override
  public int getIndexMaxTermsCount(final String indexName) throws IOException {
    return Integer.parseInt(
        esClient
            .indices()
            .getSettings(
                new GetSettingsRequest()
                    .indices(indexName)
                    .includeDefaults(true)
                    .names(MAX_TERMS_COUNT_SETTING),
                RequestOptions.DEFAULT)
            .getSetting(indexName, MAX_TERMS_COUNT_SETTING));
  }

  @Override
  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  @Override
  public void refreshIndexesInElasticsearch() {
    refreshZeebeIndices();
    refreshTasklistIndices();
  }

  @Override
  public void refreshZeebeIndices() {
    try {
      final RefreshRequest refreshRequest =
          new RefreshRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
  }

  @Override
  public void refreshTasklistIndices() {
    try {
      final RefreshRequest refreshRequest =
          new RefreshRequest(tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Tasklist Elasticsearch indices", t);
    }
  }

  @Override
  public void processAllRecordsAndWait(final TestCheck testCheck, final Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), testCheck, null, arguments);
  }

  @Override
  public void processAllRecordsAndWait(
      final TestCheck testCheck, final Supplier<Object> supplier, final Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), testCheck, supplier, arguments);
  }

  @Override
  public void processRecordsWithTypeAndWait(
      final ImportValueType importValueType, final TestCheck testCheck, final Object... arguments) {
    processRecordsAndWaitFor(getRecordsReaders(importValueType), testCheck, null, arguments);
  }

  @Override
  public void processRecordsAndWaitFor(
      final Collection<RecordsReader> readers,
      final TestCheck testCheck,
      final Supplier<Object> supplier,
      final Object... arguments) {
    long shouldImportCount = 0;
    int waitingRound = 0;
    final int maxRounds = 50;
    boolean found = testCheck.test(arguments);
    final long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      testImportListener.resetCounters();
      shouldImportCount = 0;
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshIndexesInElasticsearch();
        shouldImportCount += zeebeImporter.performOneRoundOfImportFor(readers);
      } catch (final Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
      long imported = testImportListener.getImported();
      int waitForImports = 0;
      // Wait for imports max 30 sec (60 * 500 ms)
      while (shouldImportCount != 0 && imported < shouldImportCount && waitForImports < 60) {
        waitForImports++;
        try {
          sleepFor(500);
          shouldImportCount += zeebeImporter.performOneRoundOfImportFor(readers);
        } catch (final Exception e) {
          waitingRound = 0;
          testImportListener.resetCounters();
          shouldImportCount = 0;
          LOGGER.error(e.getMessage(), e);
        }
        imported = testImportListener.getImported();
        LOGGER.debug(" {} of {} records processed", imported, shouldImportCount);
      }
      refreshTasklistIndices();
      found = testCheck.test(arguments);
      if (!found) {
        sleepFor(500);
        waitingRound++;
      }
    }
    final long finishedTime = System.currentTimeMillis() - start;

    if (found) {
      LOGGER.debug(
          "Condition {} was met in round {} ({} ms).",
          testCheck.getName(),
          waitingRound,
          finishedTime);
    } else {
      LOGGER.error(
          "Condition {} was not met after {} rounds ({} ms).",
          testCheck.getName(),
          waitingRound,
          finishedTime);
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
  public int getOpenScrollcontextSize() {
    return getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0);
  }

  @Override
  public <T> long deleteByTermsQuery(
      final String index,
      final String fieldName,
      final Collection<T> values,
      final Class<T> valueType)
      throws IOException {
    return zeebeEsClient
        .deleteByQuery(
            new DeleteByQueryRequest(index).setQuery(QueryBuilders.termsQuery(fieldName, values)),
            RequestOptions.DEFAULT)
        .getDeleted();
  }

  @Override
  public void reindex(final String sourceIndex, final String destinationIndex) throws IOException {
    esClient.reindex(
        new ReindexRequest()
            .setSourceIndices(sourceIndex)
            .setDestIndex(destinationIndex)
            .setRefresh(true),
        RequestOptions.DEFAULT);
  }

  @Override
  public void createIndex(final String indexName) throws IOException {
    esClient.indices().create(new CreateIndexRequest(indexName), RequestOptions.DEFAULT);
  }

  @Override
  public void deleteIndex(final String indexName) {
    try {
      esClient.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
    } catch (final ElasticsearchException | IOException e) {
      LOGGER.error("Could not delete index {}", indexName, e);
    }
  }

  private boolean areIndicesAreCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final GetIndexResponse response =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexPrefix + "*")
                    .indicesOptions(LENIENT_EXPAND_OPEN_FORBID_NO_INDICES_IGNORE_THROTTLED),
                RequestOptions.DEFAULT);
    final String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices;
  }

  public int getIntValueForJSON(final String path, final String fieldname, final int defaultValue) {
    final Optional<JsonNode> jsonNode = getJsonFor(path);
    if (jsonNode.isPresent()) {
      final JsonNode field = jsonNode.get().findValue(fieldname);
      if (field != null) {
        return field.asInt(defaultValue);
      }
    }
    return defaultValue;
  }

  public Optional<JsonNode> getJsonFor(final String path) {
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

  public IndexMapping getIndexTemplateMapping(final String templateName) throws IOException {
    final String indexTemplateMapping =
        esClient
            .indices()
            .getIndexTemplate(
                new GetComposableIndexTemplateRequest(templateName), RequestOptions.DEFAULT)
            .getIndexTemplates()
            .get(templateName)
            .template()
            .mappings()
            .toString();
    final Map<String, Object> mappingMetadata =
        objectMapper.readValue(
            indexTemplateMapping, new TypeReference<HashMap<String, Object>>() {});
    return new IndexMapping()
        .setDynamic((String) mappingMetadata.get("dynamic"))
        .setProperties(
            ((Map<String, Object>) mappingMetadata.get("properties"))
                .entrySet().stream()
                    .map(p -> createIndexMappingProperty(p))
                    .collect(Collectors.toSet()));
  }

  private static IndexMappingProperty createIndexMappingProperty(
      final Entry<String, Object> propertiesMapEntry) {
    return new IndexMappingProperty()
        .setName(propertiesMapEntry.getKey())
        .setTypeDefinition(propertiesMapEntry.getValue());
  }
}
