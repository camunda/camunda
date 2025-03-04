/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.config.ConnectionTypes.ELASTICSEARCH;
import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static io.camunda.exporter.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.exporter.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static io.camunda.exporter.utils.SearchDBExtension.ZEEBE_IDX_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.schema.MappingSource;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.exporter.utils.SearchDBExtension;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class CamundaExporterIT {

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private final ProtocolFactory factory = new ProtocolFactory();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  @BeforeEach
  void beforeEach() {
    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            CUSTOM_PREFIX + "-template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            CUSTOM_PREFIX + "-qualified_name",
            CUSTOM_PREFIX + "-alias",
            CUSTOM_PREFIX + "-index_name",
            "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CUSTOM_PREFIX + "-template_index_qualified_name");
  }

  @AfterEach
  public void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldOpenDifferentPartitions(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var p1Exporter = new CamundaExporter();
    final var p1Context = getContextFromConfig(config, 1);
    p1Exporter.configure(p1Context);

    final var p2Exporter = new CamundaExporter();
    final var p2Context = getContextFromConfig(config, 2);
    p2Exporter.configure(p2Context);

    // when
    final var future =
        CompletableFuture.runAsync(
            () -> {
              final var p1ExporterController = new ExporterTestController();
              p1Exporter.open(p1ExporterController);
            });

    // then
    assertThatNoException()
        .isThrownBy(
            () -> {
              final var p2ExporterController = new ExporterTestController();
              p2Exporter.open(p2ExporterController);
            });
    Awaitility.await("Partition one has been opened successfully")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(future).isNotCompletedExceptionally();
              assertThat(future).isCompleted();
            });
  }

  @TestTemplate
  void shouldOpenDifferentPartitionsWithRetention(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    final RetentionConfiguration retention = config.getHistory().getRetention();
    retention.setEnabled(true);
    retention.setPolicyName("shouldOpenDifferentPartitionsWithRetention");
    final var p1Exporter = new CamundaExporter();
    final var p1Context = getContextFromConfig(config, 1);
    p1Exporter.configure(p1Context);

    final var p2Exporter = new CamundaExporter();
    final var p2Context = getContextFromConfig(config, 2);
    p2Exporter.configure(p2Context);

    // when
    final var future =
        CompletableFuture.runAsync(
            () -> {
              final var p1ExporterController = new ExporterTestController();
              p1Exporter.open(p1ExporterController);
            });

    // then
    assertThatNoException()
        .isThrownBy(
            () -> {
              final var p2ExporterController = new ExporterTestController();
              p2Exporter.open(p2ExporterController);
            });
    Awaitility.await("Partition one has been opened successfully")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(future).isNotCompletedExceptionally();
              assertThat(future).isCompleted();
            });
  }

  @TestTemplate
  void shouldUpdateExporterPositionAfterFlushing(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var exporter = new CamundaExporter();

    final var context = getContextFromConfig(config);
    exporter.configure(context);

    final var exporterController = new ExporterTestController();
    exporter.open(exporterController);

    // when
    final var record = generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);
    assertThat(exporterController.getPosition()).isEqualTo(-1);

    exporter.export(record);

    // then
    assertThat(exporterController.getPosition()).isEqualTo(record.getPosition());
  }

  @TestTemplate
  void shouldExportRecordOnceBulkSizeReached(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    config.getBulk().setSize(2);
    final var exporter = new CamundaExporter();

    final var context = getContextFromConfig(config);
    exporter.configure(context);
    final var controllerSpy = spy(new ExporterTestController());
    exporter.open(controllerSpy);

    // when
    final var record = generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);
    final var record2 =
        generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);

    exporter.export(record);
    exporter.export(record2);
    // then
    verify(controllerSpy, never())
        .updateLastExportedRecordPosition(eq(record.getPosition()), any());
    verify(controllerSpy).updateLastExportedRecordPosition(eq(record2.getPosition()), any());
  }

  @ParameterizedTest
  @MethodSource("containerProvider")
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Container tests not supported in CI")
  void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater(
      final GenericContainer<?> container) {
    // given
    final var config = getConnectConfigForContainer(container);
    final var exporter = new CamundaExporter();

    final var context = getContextFromConfig(config);
    final ExporterTestController controller = spy(new ExporterTestController());

    exporter.configure(context);
    exporter.open(controller);

    // when
    final var currentPort = container.getFirstMappedPort();
    container.stop();
    Awaitility.await().until(() -> !container.isRunning());

    final var record = generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);

    assertThatThrownBy(() -> exporter.export(record))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("Connection refused");

    // starts the container on the same port again
    container
        .withEnv("discovery.type", "single-node")
        .setPortBindings(List.of(currentPort + ":9200"));
    container.start();

    final var record2 =
        generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);
    exporter.export(record2);

    Awaitility.await()
        .untilAsserted(() -> assertThat(controller.getPosition()).isEqualTo(record2.getPosition()));
  }

  @TestTemplate
  void shouldPeriodicallyFlushBasedOnConfiguration(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var duration = 2;
    config.getBulk().setDelay(duration);

    final var exporter = new CamundaExporter();
    exporter.configure(getContextFromConfig(config));

    // when
    final ExporterTestController controller = new ExporterTestController();
    final var spiedController = spy(controller);
    exporter.open(spiedController);

    spiedController.runScheduledTasks(Duration.ofSeconds(duration));

    // then
    verify(spiedController, times(2)).scheduleCancellableTask(eq(Duration.ofSeconds(2)), any());
  }

  @TestTemplate
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Ineligible test for AWS OS integration")
  void shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var exporter1 = createExporter(Set.of(index), Set.of(indexTemplate), config);
    final var exporter2 = createExporter(Set.of(index), Set.of(indexTemplate), config);

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    // when
    exporter1.open(new ExporterTestController());
    exporter2.open(new ExporterTestController());

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Ineligible test for AWS OS integration")
  void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var updatedExporter = createExporter(Set.of(index), Set.of(indexTemplate), config);
    final var oldExporter = createExporter(Set.of(index), Set.of(indexTemplate), config);

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    updatedExporter.open(new ExporterTestController());

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings.json");

    oldExporter.open(new ExporterTestController());

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldCreateHarmonizedSchemaEagerlyOnOpen(
      final ExporterConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var newPrefix =
        CUSTOM_PREFIX + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    config.getIndex().setPrefix(newPrefix);
    final CamundaExporter camundaExporter = new CamundaExporter();
    camundaExporter.configure(getContextFromConfig(config));

    final var adapter = ClientAdapter.of(config);
    final var mappingsBeforeOpen =
        adapter.getSearchEngineClient().getMappings(newPrefix + "*", MappingSource.INDEX);
    assertThat(mappingsBeforeOpen.keySet()).isEmpty();

    // when
    camundaExporter.open(new ExporterTestController());

    // then
    final var mappingsAfterOpen =
        adapter.getSearchEngineClient().getMappings(newPrefix + "*", MappingSource.INDEX);
    assertThat(mappingsAfterOpen.keySet())
        // we verify the names hard coded on purpose
        // to make sure no index will be accidentally dropped, names are changed or added
        .containsExactlyInAnyOrder(
            newPrefix + "-camunda-authorization-8.8.0_",
            newPrefix + "-camunda-group-8.8.0_",
            newPrefix + "-camunda-mapping-8.8.0_",
            newPrefix + "-camunda-role-8.8.0_",
            newPrefix + "-camunda-tenant-8.8.0_",
            newPrefix + "-camunda-user-8.8.0_",
            newPrefix + "-camunda-web-session-8.8.0_",
            newPrefix + "-operate-batch-operation-1.0.0_",
            newPrefix + "-operate-decision-8.3.0_",
            newPrefix + "-operate-decision-instance-8.3.0_",
            newPrefix + "-operate-decision-requirements-8.3.0_",
            newPrefix + "-operate-event-8.3.0_",
            newPrefix + "-operate-flownode-instance-8.3.1_",
            newPrefix + "-operate-import-position-8.3.0_",
            newPrefix + "-operate-incident-8.3.1_",
            newPrefix + "-operate-list-view-8.3.0_",
            newPrefix + "-operate-metric-8.3.0_",
            newPrefix + "-operate-message-8.5.0_",
            newPrefix + "-operate-operation-8.4.1_",
            newPrefix + "-operate-post-importer-queue-8.3.0_",
            newPrefix + "-operate-process-8.3.0_",
            newPrefix + "-operate-sequence-flow-8.3.0_",
            newPrefix + "-operate-variable-8.3.0_",
            newPrefix + "-operate-job-8.6.0_",
            newPrefix + "-tasklist-draft-task-variable-8.3.0_",
            newPrefix + "-tasklist-form-8.4.0_",
            newPrefix + "-tasklist-metric-8.3.0_",
            newPrefix + "-tasklist-task-8.5.0_",
            newPrefix + "-tasklist-task-variable-8.3.0_",
            newPrefix + "-tasklist-import-position-8.2.0_");
  }

  @TestTemplate
  void shouldExportRecord(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    // given
    final var valueType = ValueType.VARIABLE;
    final Record record =
        generateRecordWithSupportedBrokerVersion(valueType, VariableIntent.CREATED);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());
    final var expectedHandlers =
        resourceProvider.getExportHandlers().stream()
            .filter(exportHandler -> exportHandler.getHandledValueType() == valueType)
            .filter(exportHandler -> exportHandler.handlesRecord(record))
            .toList();

    final CamundaExporter camundaExporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    camundaExporter.configure(exporterTestContext);
    camundaExporter.open(new ExporterTestController());

    // when
    camundaExporter.export(record);

    // then
    assertThat(expectedHandlers).isNotEmpty();
    expectedHandlers.forEach(
        exportHandler -> {
          final ExporterEntity expectedEntity = getExpectedEntity(record, exportHandler);
          final ExporterEntity<?> responseEntity;
          try {
            responseEntity =
                clientAdapter.get(
                    expectedEntity.getId(),
                    exportHandler.getIndexName(),
                    exportHandler.getEntityType());
          } catch (final IOException e) {
            fail("Failed to find expected entity " + expectedEntity, e);
            return;
          }

          assertThat(responseEntity)
              .describedAs(
                  "Handler [%s] correctly handles a [%s] record",
                  exportHandler.getClass().getSimpleName(), exportHandler.getHandledValueType())
              .isEqualTo(expectedEntity);
        });
  }

  @TestTemplate
  void shouldNotFailWhenUpdatingOperationWithNoDocument(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    // given
    final ValueType valueType = ValueType.INCIDENT;
    final long notExistingOperationReference = 9876543210L;
    final Record record =
        factory.generateRecord(
            valueType,
            r ->
                r.withBrokerVersion("8.8.0")
                    .withIntent(IncidentIntent.RESOLVED)
                    .withTimestamp(System.currentTimeMillis())
                    .withOperationReference(notExistingOperationReference));
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    final CamundaExporter camundaExporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    camundaExporter.configure(exporterTestContext);
    camundaExporter.open(new ExporterTestController());

    // act
    assertThatCode(() -> camundaExporter.export(record)).doesNotThrowAnyException();
  }

  @TestTemplate
  void shouldThrowIfDateFormatIsInvalid(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    // given
    final ValueType valueType = ValueType.INCIDENT;
    final long invalidTimestamp = 8109027450636607488L;
    final Record record =
        factory.generateRecord(
            valueType,
            r ->
                r.withBrokerVersion("8.8.0")
                    .withIntent(IncidentIntent.RESOLVED)
                    .withTimestamp(invalidTimestamp));
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    final CamundaExporter camundaExporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    camundaExporter.configure(exporterTestContext);
    camundaExporter.open(new ExporterTestController());

    // act
    assertThatThrownBy(() -> camundaExporter.export(record))
        .isInstanceOf(ExporterException.class)
        .cause()
        .isInstanceOf(PersistenceException.class);
  }

  @TestTemplate
  void shouldNotExport870RecordButStillUpdateLastExportedPosition(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    final var recordPosition = 123456789L;
    final var record =
        factory.generateRecord(
            ValueType.USER,
            r -> r.withBrokerVersion("8.7.0").withPosition(recordPosition),
            UserIntent.CREATED);

    final CamundaExporter camundaExporter = new CamundaExporter();
    final var controller = new ExporterTestController();
    camundaExporter.configure(getContextFromConfig(config));
    camundaExporter.open(controller);

    // when
    camundaExporter.export(record);

    // then
    assertThat(controller.getPosition()).isEqualTo(recordPosition);

    final var handlersForRecordAndExpectedEntityId =
        getHandlers(config).stream()
            .filter(handler -> handler.getHandledValueType().equals(record.getValueType()))
            .filter(handler -> handler.handlesRecord(record))
            .collect(
                Collectors.toMap(
                    Function.identity(), handler -> handler.generateIds(record).getFirst()));

    assertThat(handlersForRecordAndExpectedEntityId).isNotEmpty();

    for (final var entry : handlersForRecordAndExpectedEntityId.entrySet()) {
      final var handler = entry.getKey();
      final var entityId = entry.getValue();

      assertThat(clientAdapter.get(entityId, handler.getIndexName(), handler.getEntityType()))
          .isNull();
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends ExporterEntity<T>, R extends RecordValue> Set<ExportHandler<T, R>> getHandlers(
      final ExporterConfiguration config) {
    final DefaultExporterResourceProvider defaultExporterResourceProvider =
        new DefaultExporterResourceProvider();
    defaultExporterResourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    return defaultExporterResourceProvider.getExportHandlers().stream()
        .map(handler -> (ExportHandler<T, R>) handler)
        .collect(Collectors.toSet());
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> S getExpectedEntity(
      final io.camunda.zeebe.protocol.record.Record<T> record, final ExportHandler<S, T> handler) {
    final var entityId = handler.generateIds(record).getFirst();
    final var expectedEntity = handler.createNewEntity(entityId);
    handler.updateEntity(record, expectedEntity);

    return expectedEntity;
  }

  private Record<?> generateRecordWithSupportedBrokerVersion(
      final ValueType valueType, final Intent intent) {
    return factory.generateRecord(valueType, r -> r.withBrokerVersion("8.8.0"), intent);
  }

  private static Stream<Arguments> containerProvider() {
    return Stream.of(
        Arguments.of(TestSearchContainers.createDefeaultElasticsearchContainer()),
        Arguments.of(TestSearchContainers.createDefaultOpensearchContainer()));
  }

  private ExporterConfiguration getConnectConfigForContainer(final GenericContainer<?> container) {
    container.start();
    Awaitility.await().until(container::isRunning);

    final var config = new ExporterConfiguration();
    config.getConnect().setUrl("http://localhost:" + container.getFirstMappedPort());
    config.getBulk().setSize(1);

    if (container.getDockerImageName().contains(ELASTICSEARCH.getType())) {
      config.getConnect().setType(ELASTICSEARCH.getType());
    }

    if (container.getDockerImageName().contains(ConnectionTypes.OPENSEARCH.getType())) {
      config.getConnect().setType(ConnectionTypes.OPENSEARCH.getType());
    }

    return config;
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return getContextFromConfig(config, 1);
  }

  private Context getContextFromConfig(final ExporterConfiguration config, final int partitionId) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config))
        .setPartitionId(partitionId);
  }

  private CamundaExporter createExporter(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors,
      final ExporterConfiguration config) {
    final var exporter =
        new CamundaExporter(mockResourceProvider(indexDescriptors, templateDescriptors, config));
    exporter.configure(getContextFromConfig(config));
    exporter.open(new ExporterTestController());

    return exporter;
  }

  private ExporterResourceProvider mockResourceProvider(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors,
      final ExporterConfiguration config) {
    final var provider = mock(DefaultExporterResourceProvider.class, CALLS_REAL_METHODS);
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  @Nested
  class ImportersCompletedTests {
    private final ExporterTestController controller = spy(new ExporterTestController());
    private final CamundaExporter camundaExporter = new CamundaExporter();
    private final int partitionId = 1;
    private final String importPositionIndexName =
        new ImportPositionIndex(CUSTOM_PREFIX, true).getFullQualifiedName();

    @BeforeEach
    void setup() {
      controller.resetScheduledTasks();
      controller.resetLastRanAt();
    }

    @TestTemplate
    void shouldFlushIfImporterNotCompletedButNoZeebeIndices(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      config.getIndex().setZeebeIndexPrefix(ZEEBE_IDX_PREFIX);
      createSchemas(config, clientAdapter);
      indexImportPositionEntity("decision", false, clientAdapter);
      clientAdapter.refresh();

      // given
      final var context = spy(getContextFromConfig(config));
      doReturn(partitionId).when(context).getPartitionId();
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      controller.runScheduledTasks(Duration.ofMinutes(1));

      // when
      final var record =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      camundaExporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(record.getPosition());
      verify(controller, times(1))
          .updateLastExportedRecordPosition(eq(record.getPosition()), any());

      final var authHandler =
          getHandlers(config).stream()
              .filter(handler -> handler.getHandledValueType().equals(record.getValueType()))
              .filter(handler -> handler.handlesRecord(record))
              .findFirst()
              .orElseThrow();
      final var recordId = authHandler.generateIds(record).getFirst();

      assertThat(
              clientAdapter.get(recordId, authHandler.getIndexName(), authHandler.getEntityType()))
          .isNotNull();
    }

    @TestTemplate
    void shouldNotFlushIfImportersAreNotCompletedAndThereAreZeebeIndices(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      final String zeebeIndexPrefix = CUSTOM_PREFIX + "-zeebe-record";
      config.getIndex().setZeebeIndexPrefix(zeebeIndexPrefix);
      createSchemas(config, clientAdapter);
      clientAdapter.index("1", zeebeIndexPrefix + "-decision", Map.of("key", "12345"));

      // adds a not complete position index document so exporter sees importing as not yet completed
      indexImportPositionEntity("decision", false, clientAdapter);
      clientAdapter.refresh();

      final var context = spy(getContextFromConfig(config));
      doReturn(partitionId).when(context).getPartitionId();
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      // when
      final var record =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      camundaExporter.export(record);

      // if importers completed this would trigger scheduled flush which would result in the
      // record being visible in ES/OS
      controller.runScheduledTasks(Duration.ofSeconds(config.getBulk().getDelay()));

      // then
      assertThat(controller.getPosition()).isEqualTo(-1);
      verify(controller, never()).updateLastExportedRecordPosition(eq(record.getPosition()), any());

      final var authHandler =
          getHandlers(config).stream()
              .filter(handler -> handler.getHandledValueType().equals(record.getValueType()))
              .filter(handler -> handler.handlesRecord(record))
              .findFirst()
              .orElseThrow();
      final var recordId = authHandler.generateIds(record).getFirst();

      assertThat(
              clientAdapter.get(recordId, authHandler.getIndexName(), authHandler.getEntityType()))
          .isNull();
    }

    @TestTemplate
    void shouldFlushIfImportersAreCompleted(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      final var context = spy(getContextFromConfig(config));
      doReturn(partitionId).when(context).getPartitionId();
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      controller.runScheduledTasks(Duration.ofMinutes(1));

      // when
      final var record =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      camundaExporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(record.getPosition());
      verify(controller, times(1))
          .updateLastExportedRecordPosition(eq(record.getPosition()), any());

      final var authHandler =
          getHandlers(config).stream()
              .filter(handler -> handler.getHandledValueType().equals(record.getValueType()))
              .filter(handler -> handler.handlesRecord(record))
              .findFirst()
              .orElseThrow();
      final var recordId = authHandler.generateIds(record).getFirst();

      assertThat(
              clientAdapter.get(recordId, authHandler.getIndexName(), authHandler.getEntityType()))
          .isNotNull();
    }

    @TestTemplate
    void shouldRunDelayedFlushIfNotWaitingForImportersRegardlessOfImporterIndexState(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      createSchemas(config, clientAdapter);
      // an incomplete position index document so exporter sees importing as not yet completed
      indexImportPositionEntity("decision", false, clientAdapter);
      clientAdapter.refresh();

      // increase bulk size so we flush via delayed flush, adding fewer records than bulk size
      config.getBulk().setSize(5);
      // ignore importer state, to export regardless of the importer state
      config.getIndex().setShouldWaitForImporters(false);

      final var context = spy(getContextFromConfig(config));
      doReturn(partitionId).when(context).getPartitionId();
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      // when
      final var record =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      camundaExporter.export(record);

      // as the importer state is ignored, this should trigger a flush still resulting in the
      // record being visible in ES/OS
      controller.runScheduledTasks(Duration.ofSeconds(config.getBulk().getDelay()));

      // then
      assertThat(controller.getPosition()).isEqualTo(record.getPosition());
      verify(controller, times(1))
          .updateLastExportedRecordPosition(eq(record.getPosition()), any());

      final var authHandler =
          getHandlers(config).stream()
              .filter(handler -> handler.getHandledValueType().equals(record.getValueType()))
              .filter(handler -> handler.handlesRecord(record))
              .findFirst()
              .orElseThrow();
      final var recordId = authHandler.generateIds(record).getFirst();

      assertThat(
              clientAdapter.get(recordId, authHandler.getIndexName(), authHandler.getEntityType()))
          .isNotNull();
    }

    @TestTemplate
    @DisabledIfSystemProperty(
        named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
        matches = "^(?=\\s*\\S).*$",
        disabledReason = "Ineligible test for AWS OS integration")
    void shouldFailIfWaitingForImportersAndCachedRecordsCountReachesBulkSize(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      assertThat(config.getBulk().getSize()).isEqualTo(1);
      final String zeebeIndexPrefix = CUSTOM_PREFIX + "-zeebe-record";
      config.getIndex().setZeebeIndexPrefix(zeebeIndexPrefix);

      // Simulate existing zeebe index so it will not skip the wait for importers
      clientAdapter.index("1", zeebeIndexPrefix + "-decision", Map.of("key", "12345"));

      // if schemas are never created then import position indices do not exist and all checks about
      // whether the importers are completed will return false.
      config.setCreateSchema(false);
      final var context = getContextFromConfig(config);
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      clientAdapter.index(
          context.getPartitionId() + "-job",
          importPositionIndexName,
          new ImportPositionEntity().setCompleted(false).setPartitionId(context.getPartitionId()));

      // when
      final var record =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      camundaExporter.export(record);

      final var record2 =
          factory.generateRecord(
              ValueType.USER,
              r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
              UserIntent.CREATED);

      // then
      assertThatThrownBy(() -> camundaExporter.export(record2))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              String.format(
                  "Reached the max bulk size amount of cached records [%d] while waiting for importers to finish",
                  config.getBulk().getSize()));
    }

    private void indexImportPositionEntity(
        final String aliasName, final boolean completed, final SearchClientAdapter client)
        throws IOException {
      final var entity =
          new ImportPositionEntity()
              .setPartitionId(partitionId)
              .setAliasName(aliasName)
              .setCompleted(completed);

      client.index(entity.getId(), importPositionIndexName, entity);
    }

    private void createSchemas(
        final ExporterConfiguration config, final SearchClientAdapter adapter) throws IOException {
      final var exporter = new CamundaExporter();
      exporter.configure(getContextFromConfig(config));
      exporter.open(new ExporterTestController());

      adapter.refresh();
    }
  }
}
