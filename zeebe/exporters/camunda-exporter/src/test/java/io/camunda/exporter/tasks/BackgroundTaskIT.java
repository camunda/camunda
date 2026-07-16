/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BackgroundTaskIT<T extends BackgroundTask> {
  protected static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskIT.class);
  protected static final int PARTITION_ID = 1;
  protected static final AtomicLong ID_GENERATOR = new AtomicLong(1);
  protected static final Instant NOW = Instant.parse("2026-05-01T10:26:00Z");
  protected static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(30L);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  protected CamundaExporterMetrics exporterMetrics;
  protected ExecutorService executor;
  protected Context context;
  protected String testPrefix;

  private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

  @BeforeEach
  protected void setup() {
    context =
        new ExporterTestContext().setPartitionId(PARTITION_ID).setClock(InstantSource.fixed(NOW));
    exporterMetrics = mock(CamundaExporterMetrics.class);
    executor = Executors.newSingleThreadExecutor();
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase(Locale.ROOT);
  }

  @AfterEach
  protected void teardown() throws Exception {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(testPrefix + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(testPrefix + "*"));

    if (executor != null) {
      executor.shutdown();
      executor = null;
    }

    CloseHelper.quietCloseAll(resourcesToClose);
    resourcesToClose.clear();
  }

  @TestTemplate
  protected void shouldExecuteWithoutErrorsWhenNothingToDo(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // just a basic smoke test to verify that the task runs and creates well-formed queries etc
    // when there's nothing to actually do
    withTask(
        config,
        (task, resources) -> {
          // when
          final var archived = task.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);
        });
  }

  protected abstract T createBackgroundTask(
      ExporterConfiguration config, ExporterResourceProvider resourceProvider);

  protected void updateConfig(final ExporterConfiguration config) {
    // default implementation does nothing
  }

  protected void withTask(
      final ExporterConfiguration config, final BackgroundTaskConsumer<T> taskConsumer)
      throws Exception {
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfShards(3);
    config.getIndex().setNumberOfReplicas(0);
    updateConfig(config);
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    try (final T task = createBackgroundTask(config, exporterResourceProvider)) {
      taskConsumer.accept(task, exporterResourceProvider);
    }
  }

  private ExporterResourceProvider exporterResourceProvider(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString(), any())).thenReturn(k -> null);
    when(cacheProvider.getBatchOperationCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getDecisionRequirementsCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getFormCacheLoader(anyString())).thenReturn(k -> null);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        cacheProvider,
        context,
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());
    return resourceProvider;
  }

  protected void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    client.index(entity.getId(), indexDescriptor.getFullQualifiedName(), entity);
  }

  protected void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> child)
      throws IOException {
    client.index(child.getId(), parent.getId(), indexDescriptor.getFullQualifiedName(), child);
  }

  protected ElasticsearchAsyncClient createAsyncESClient(final ExporterConfiguration config) {
    final var connector = new ElasticsearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected OpenSearchAsyncClient createOSAsyncClient(final ExporterConfiguration config) {
    final var connector = new OpensearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected <R extends AutoCloseable> R closeLater(final R resource) {
    resourcesToClose.add(resource);
    return resource;
  }

  protected interface BackgroundTaskConsumer<T extends BackgroundTask> {
    void accept(T task, ExporterResourceProvider resourceProvider) throws Exception;
  }
}
