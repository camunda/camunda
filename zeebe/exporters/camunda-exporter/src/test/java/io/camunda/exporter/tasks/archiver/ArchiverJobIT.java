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
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ArchiverJobIT<T extends ArchiverJob<?>> {
  protected static final Logger LOGGER = LoggerFactory.getLogger(ArchiverJobIT.class);
  protected static final int PARTITION_ID = 1;
  protected static final AtomicLong ID_GENERATOR = new AtomicLong(1);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  protected CamundaExporterMetrics exporterMetrics;
  protected ExecutorService executor;
  protected Context context;

  private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

  @BeforeEach
  void setup() {
    context = new ExporterTestContext().setPartitionId(PARTITION_ID);
    exporterMetrics = new CamundaExporterMetrics(context.getMeterRegistry());
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void teardown() throws Exception {
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

    CloseHelper.quietCloseAll(resourcesToClose);
    resourcesToClose.clear();
  }

  @TestTemplate
  void shouldExecuteWithoutErrorsWhenNothingToArchive(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // just a basic smoke test to verify that the job runs and creates well-formed queries etc
    // when there's nothing to actually archive
    withArchiverJob(
        config,
        (job, resources) -> {
          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(0);
        });
  }

  void withArchiverJob(final ExporterConfiguration config, final ArchiveJobConsumer<T> jobConsumer)
      throws Exception {
    config.getHistory().getRetention().setEnabled(true);
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    final var repository = createArchiverRepository(config, exporterResourceProvider);
    try (final T job = createArchiveJob(config, exporterResourceProvider, repository)) {
      jobConsumer.accept(job, exporterResourceProvider);
    }
  }

  protected <E extends ExporterEntity<E>> E create(final Supplier<E> constructor) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var entity = constructor.get();
    entity.setId(String.valueOf(id));
    return entity;
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

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity,
      final String datedIndexSuffix)
      throws IOException {
    verifyMoved(templateDescriptor, client, entity, parent.getId(), datedIndexSuffix);
  }

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String datedIndexSuffix)
      throws IOException {
    verifyMoved(templateDescriptor, client, entity, (String) null, datedIndexSuffix);
  }

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing,
      final String datedIndexSuffix)
      throws IOException {
    // should no longer be in the original index
    final var originalIndexEntity =
        client.get(
            entity.getId(), routing, templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity)
        .describedAs(
            "Expected %s to have been deleted from %s",
            entity, templateDescriptor.getFullQualifiedName())
        .isNull();

    // should now be in the dated index
    final var dateIndex = templateDescriptor.getFullQualifiedName() + datedIndexSuffix;
    final var newIndexEntity = client.get(entity.getId(), routing, dateIndex, entity.getClass());
    assertThat(newIndexEntity)
        .describedAs("Expected %s to have been moved to %s", entity, dateIndex)
        .isEqualTo(entity);
  }

  protected void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyNotMoved(templateDescriptor, client, entity, (String) null);
  }

  protected void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyNotMoved(templateDescriptor, client, entity, parent.getId());
  }

  private void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing)
      throws IOException {
    final var originalIndexEntity =
        client.get(
            entity.getId(), routing, templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity)
        .describedAs(
            "Expected %s to still be in %s", entity, templateDescriptor.getFullQualifiedName())
        .isEqualTo(entity);
  }

  private ExporterResourceProvider exporterResourceProvider(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString())).thenReturn(k -> null);
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

  private ArchiverRepository createArchiverRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              createAsyncESClient(config),
              executor,
              exporterMetrics,
              LOGGER));
    } else {
      final var asyncClient = createOSAsyncClient(config);
      final var genericClient =
          new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions());

      return closeLater(
          new OpenSearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              asyncClient,
              genericClient,
              executor,
              exporterMetrics,
              LOGGER));
    }
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

  abstract T createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository);

  interface ArchiveJobConsumer<T> {
    void accept(T job, ExporterResourceProvider resourceProvider) throws Exception;
  }
}
