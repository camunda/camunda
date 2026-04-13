/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ArchiverJobIT<T extends ArchiverJob> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverJobIT.class);
  private static final int PARTITION_ID = 1;

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private Context context;
  private CamundaExporterMetrics exporterMetrics;
  private ExecutorService executor;

  @BeforeEach
  void setup() {
    context = new ExporterTestContext().setPartitionId(PARTITION_ID);
    exporterMetrics = new CamundaExporterMetrics(context.getMeterRegistry());
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void teardown() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));

    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }

  @TestTemplate
  void shouldExecuteWithoutErrorsWhenNothingToArchive(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    withArchiverJob(
        config,
        job -> {
          final var archived = job.execute().toCompletableFuture().join();
          assertThat(archived).isEqualTo(0);
        });
  }

  void withArchiverJob(final ExporterConfiguration config, final Consumer<T> jobConsumer)
      throws Exception {
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    try (final ArchiverRepository repository =
            createArchiverRepository(config, exporterResourceProvider);
        final T job =
            createArchiveJob(
                config, exporterResourceProvider, repository, exporterMetrics, LOGGER, executor)) {
      jobConsumer.accept(job);
    }
  }

  private ExporterResourceProvider exporterResourceProvider(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getBatchOperationCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getFormCacheLoader(anyString())).thenReturn(k -> null);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        cacheProvider,
        context.getMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());
    return resourceProvider;
  }

  private ArchiverRepository createArchiverRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      final var connector = new ElasticsearchConnector(config.getConnect());
      final ElasticsearchAsyncClient asyncClient = connector.createAsyncClient();

      return new ElasticsearchArchiverRepository(
          PARTITION_ID,
          config.getHistory(),
          resourceProvider,
          asyncClient,
          executor,
          exporterMetrics,
          LOGGER);
    } else {
      final var connector = new OpensearchConnector(config.getConnect());
      final var asyncClient = connector.createAsyncClient();
      final var genericClient =
          new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions());

      return new OpenSearchArchiverRepository(
          PARTITION_ID,
          config.getHistory(),
          resourceProvider,
          asyncClient,
          genericClient,
          executor,
          exporterMetrics,
          LOGGER);
    }
  }

  abstract T createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository,
      CamundaExporterMetrics exporterMetrics,
      Logger logger,
      Executor executor);
}
