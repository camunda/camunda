/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.config.ConnectionTypes.ELASTICSEARCH;
import static io.camunda.exporter.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.exporter.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static io.camunda.exporter.utils.SearchDBExtension.ZEEBE_IDX_PREFIX;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.exporter.utils.SearchDBExtension;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
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
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
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
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
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
      final GenericContainer<?> container) throws IOException {
    // given
    final var config = getConnectConfigForContainer(container);
    createSchemas(config);
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
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
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
  void shouldExportRecord(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
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
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
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
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
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
    createSchemas(config);
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

  private void createSchemas(final ExporterConfiguration config) throws IOException {
    final var indexDescriptors =
        new IndexDescriptors(
            config.getIndex().getPrefix(), config.getConnect().getTypeEnum().isElasticSearch());
    try (final ClientAdapter clientAdapter = ClientAdapter.of(config.getConnect())) {
      new SchemaManager(
              clientAdapter.getSearchEngineClient(),
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              config,
              clientAdapter.objectMapper())
          .startup();
    }
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
    void shouldMarkImportersAsCompletedEvenIfVersion88ZeebeIndicesExist(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      final String zeebeIndexPrefix = CUSTOM_PREFIX + "-zeebe-record";
      config.getIndex().setZeebeIndexPrefix(zeebeIndexPrefix);
      createSchemas(config);
      clientAdapter.index("1", zeebeIndexPrefix + "-decision_8.8.0", Map.of("key", "12345"));

      // adds a not complete position index document so exporter sees importing as not yet completed
      indexImportPositionEntity("decision", false, clientAdapter);
      clientAdapter.refresh();

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
    }

    @TestTemplate
    void shouldFlushIfImporterNotCompletedButNoZeebeIndices(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      config.getIndex().setZeebeIndexPrefix(ZEEBE_IDX_PREFIX);
      createSchemas(config);
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
    void shouldNotFlushIf87IndicesExistToBeImported(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      final String zeebeIndexPrefix = CUSTOM_PREFIX + "-zeebe-record";
      config.getIndex().setZeebeIndexPrefix(zeebeIndexPrefix);
      createSchemas(config);
      clientAdapter.index("1", zeebeIndexPrefix + "-decision_8.7.0_", Map.of("key", "12345"));

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
      createSchemas(config);
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
      createSchemas(config);
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
      clientAdapter.index("1", zeebeIndexPrefix + "-decision_8.7.0_", Map.of("key", "12345"));

      // if schemas are never created then import position indices do not exist and all checks about
      // whether the importers are completed will return false.
      final var provider = new DefaultExporterResourceProvider();
      final var indexDescriptors = mock(IndexDescriptors.class);
      when(indexDescriptors.indices())
          .thenReturn(List.of()) // for schemaManager.isSchemaReadyForUse()
          .thenReturn( // for importPositionIndices that is passed for
              // searchEngineClient.importersCompleted;
              List.of(
                  new ImportPositionIndex(
                      CUSTOM_PREFIX, config.getConnect().getTypeEnum().isElasticSearch())));
      when(indexDescriptors.templates()).thenReturn(emptyList());
      final var camundaExporter = new CamundaExporter(provider);
      final var context = getContextFromConfig(config);
      camundaExporter.configure(context);
      provider.setIndexDescriptors(indexDescriptors);
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
  }
}
