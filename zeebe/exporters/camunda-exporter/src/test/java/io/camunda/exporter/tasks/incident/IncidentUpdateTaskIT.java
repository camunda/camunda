/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

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
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IncidentUpdateTaskIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateTaskIT.class);
  private static final int PARTITION_ID = 1;
  private static final Instant NOW = Instant.parse("2026-05-01T10:26:00Z");
  private static final int BATCH_SIZE = 10;
  private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(30L);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private CamundaExporterMetrics exporterMetrics;
  private ExecutorService executor;
  private IncidentNotifier incidentNotifier;
  private ExporterMetadata exporterMetadata;
  private Context context;
  private String testPrefix;

  private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

  @BeforeEach
  void setup() {
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    context =
        new ExporterTestContext().setPartitionId(PARTITION_ID).setClock(InstantSource.fixed(NOW));
    exporterMetrics = new CamundaExporterMetrics(context.getMeterRegistry());
    executor = Executors.newSingleThreadExecutor();
    incidentNotifier = mock(IncidentNotifier.class);
    exporterMetadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  }

  @AfterEach
  void teardown() throws Exception {
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
  void shouldExecuteWithoutErrorsWhenNoIncidentsToUpdate(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // just a basic smoke test to verify that the job runs and creates well-formed queries etc
    // when there's nothing to actually update
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(0);
        });
  }

  @TestTemplate
  void shouldThrowExceptionWhenIncidentsToUpdateAreNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("incident-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(9999L)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(testPrefix);

          // when
          final var updated = job.execute();

          // then
          assertThat(updated)
              .failsWithin(UPDATE_TIMEOUT)
              .withThrowableThat()
              .withRootCauseInstanceOf(ExporterException.class)
              .withMessageContaining(
                  "Failed to fetch incidents associated with post-export updates")
              .withMessageContaining("Missing incident IDs: [[9999]]");
        });
  }

  void withIncidentUpdateTask(
      final ExporterConfiguration config, final IncidentUpdateTaskConsumer taskConsumer)
      throws Exception {
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfShards(3);
    config.getIndex().setNumberOfReplicas(0);
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    final var repository = createIncidentUpdateRepository(config, exporterResourceProvider);

    try (final IncidentUpdateTask task =
        new IncidentUpdateTask(
            exporterMetadata,
            repository,
            false,
            BATCH_SIZE,
            executor,
            incidentNotifier,
            exporterMetrics,
            LOGGER,
            Duration.ofSeconds(1))) {
      taskConsumer.accept(task, exporterResourceProvider);
    }
  }

  private void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    client.index(entity.getId(), indexDescriptor.getFullQualifiedName(), entity);
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

  private IncidentUpdateRepository createIncidentUpdateRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {

    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    final var flowNodeTemplate =
        resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);
    final var incidentTemplate =
        resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class);
    final var postImporterTemplate =
        resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchIncidentUpdateRepository(
              PARTITION_ID,
              postImporterTemplate.getAlias(),
              postImporterTemplate.getFullQualifiedName(),
              incidentTemplate.getAlias(),
              listViewTemplate.getAlias(),
              listViewTemplate.getFullQualifiedName(),
              flowNodeTemplate.getAlias(),
              operationTemplate.getAlias(),
              createAsyncESClient(config),
              executor,
              LOGGER));
    } else {
      return closeLater(
          new OpenSearchIncidentUpdateRepository(
              PARTITION_ID,
              postImporterTemplate.getAlias(),
              postImporterTemplate.getFullQualifiedName(),
              incidentTemplate.getAlias(),
              listViewTemplate.getAlias(),
              listViewTemplate.getFullQualifiedName(),
              flowNodeTemplate.getAlias(),
              operationTemplate.getAlias(),
              createOSAsyncClient(config),
              executor,
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

  interface IncidentUpdateTaskConsumer {
    void accept(IncidentUpdateTask task, ExporterResourceProvider exporterResourceProvider)
        throws Exception;
  }
}
