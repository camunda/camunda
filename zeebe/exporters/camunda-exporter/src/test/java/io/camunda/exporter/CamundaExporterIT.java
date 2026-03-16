/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.config.ConnectionTypes.ELASTICSEARCH;
import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static io.camunda.search.test.utils.SearchDBExtension.ZEEBE_IDX_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.VariableHandler;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.exporter.utils.EntitySizeEstimator;
import io.camunda.exporter.utils.ExporterThreadLeakExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
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
@ExtendWith(ExporterThreadLeakExtension.class)
final class CamundaExporterIT {

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private final ProtocolFactory factory = new ProtocolFactory();

  /** Primary exporter under test — closed automatically after each test. */
  private CamundaExporter exporter;

  /**
   * Secondary exporter used only when a test needs multiple exporters (e.g. different partitions) —
   * closed automatically after each test.
   */
  private CamundaExporter secondExporter;

  @AfterEach
  public void afterEach() throws IOException {
    closeExporters();
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  private void closeExporters() {
    if (exporter != null) {
      exporter.close();
    }
    if (secondExporter != null) {
      secondExporter.close();
    }
  }

  @TestTemplate
  void shouldOpenDifferentPartitions(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
    exporter = new CamundaExporter();
    final var p1Context = getContextFromConfig(config, 1);
    exporter.configure(p1Context);

    secondExporter = new CamundaExporter();
    final var p2Context = getContextFromConfig(config, 2);
    secondExporter.configure(p2Context);

    // when
    final var future =
        CompletableFuture.runAsync(
            () -> {
              final var p1ExporterController = new ExporterTestController();
              exporter.open(p1ExporterController);
            });

    // then
    assertThatNoException()
        .isThrownBy(
            () -> {
              final var p2ExporterController = new ExporterTestController();
              secondExporter.open(p2ExporterController);
            });
    await("Partition one has been opened successfully")
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
    exporter = new CamundaExporter();

    final var context = getContextFromConfig(config);
    exporter.configure(context);

    final var exporterController = new ExporterTestController();
    exporter.open(exporterController);

    // when
    final var record = generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);
    assertThat(exporterController.getPosition()).isEqualTo(-1);

    exporter.export(record);

    // flushes are now async so close() is called to force a wait for pending flushes to finish
    exporter.close();

    // then
    assertThat(exporterController.getPosition()).isEqualTo(record.getPosition());
  }

  @TestTemplate
  void shouldExportRecordOnceBulkSizeReached(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
    config.getBulk().setSize(2);
    exporter = new CamundaExporter();

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
    exporter.close();

    // then
    verify(controllerSpy, never())
        .updateLastExportedRecordPosition(eq(record.getPosition()), any());

    // two update positions
    // - one occurs from async flush completing and setting position
    // - second occurs on close call which invokes a synchronous flush
    verify(controllerSpy, times(2))
        .updateLastExportedRecordPosition(eq(record2.getPosition()), any());
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
    exporter = new CamundaExporter();

    final var context = getContextFromConfig(config);
    final ExporterTestController controller = spy(new ExporterTestController());

    exporter.configure(context);
    exporter.open(controller);

    // when
    final var currentPort = container.getFirstMappedPort();
    container.stop();
    await().until(() -> !container.isRunning());

    final var record = generateRecordWithSupportedBrokerVersion(ValueType.USER, UserIntent.CREATED);

    exporter.export(record);

    // the export above fails in the async supplier, and it requires a call to .join that future so
    // that the error is surfaced
    assertThatThrownBy(() -> controller.runScheduledTasks(Duration.ofSeconds(1)))
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
    exporter.close();

    await()
        .untilAsserted(() -> assertThat(controller.getPosition()).isEqualTo(record2.getPosition()));
  }

  @TestTemplate
  void shouldPeriodicallyFlushBasedOnConfiguration(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws IOException {
    // given
    createSchemas(config);
    final var duration = 2;
    config.getBulk().setDelay(duration);

    exporter = new CamundaExporter();
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

    exporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    exporter.configure(exporterTestContext);
    exporter.open(new ExporterTestController());

    // when
    exporter.export(record);
    exporter.close();

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

    exporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    exporter.configure(exporterTestContext);
    exporter.open(new ExporterTestController());

    // act
    assertThatCode(() -> exporter.export(record)).doesNotThrowAnyException();
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
                    .withIntent(IncidentIntent.CREATED)
                    .withTimestamp(invalidTimestamp));
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    exporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    exporter.configure(exporterTestContext);
    final var controller = new ExporterTestController();
    exporter.open(controller);

    // act
    exporter.export(record);
    assertThatThrownBy(() -> controller.runScheduledTasks(Duration.ofSeconds(1)))
        .isInstanceOf(ExporterException.class)
        .rootCause()
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

  @TestTemplate
  void shouldFailToOpenWhenSchemaMissingThenOpenAfterSchemaCreationAndExport(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // Do not create schema yet
    exporter = spy(new CamundaExporter());
    final var context = getContextFromConfig(config);
    exporter.configure(context);

    // Reset the spy interactions to only capture interactions during open
    reset(exporter);

    // First open should fail because schema is not yet created
    final var firstController = new ExporterTestController();
    assertThatThrownBy(() -> exporter.open(firstController))
        .isInstanceOf(ExporterException.class)
        .hasMessage("Schema is not ready for use");

    // Exporter should have closed its resources in the failure path
    verify(exporter, times(1)).close();

    // Create schema now
    createSchemas(config);

    // Second open succeeds
    final var secondController = new ExporterTestController();
    assertThatNoException().isThrownBy(() -> exporter.open(secondController));

    // Export a record
    final var record =
        factory.generateRecord(
            ValueType.USER,
            r -> r.withBrokerVersion("8.8.0").withTimestamp(System.currentTimeMillis()),
            UserIntent.CREATED);
    exporter.export(record);
    exporter.close();

    // Position updated
    assertThat(secondController.getPosition()).isEqualTo(record.getPosition());

    // Entity written to search engine
    final var handler =
        getHandlers(config).stream()
            .filter(h -> h.getHandledValueType().equals(record.getValueType()))
            .filter(h -> h.handlesRecord(record))
            .findFirst()
            .orElseThrow();
    final var recordId = handler.generateIds(record).getFirst();
    assertThat(clientAdapter.get(recordId, handler.getIndexName(), handler.getEntityType()))
        .isNotNull();
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
    await().until(container::isRunning);

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

  @TestTemplate
  void shouldFlushWhenMemoryLimitReached(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    exporter = new CamundaExporter();

    final var memoryLimit = 3;
    config.getBulk().setMemoryLimit(memoryLimit);
    final var context = getContextFromConfig(config);
    exporter.configure(context);

    final var exporterController = new ExporterTestController();
    exporter.open(exporterController);

    // when
    final var largeString = RandomStringUtils.randomAlphanumeric(250_000);
    final var varEntity = new VariableEntity();
    varEntity.setFullValue(largeString);
    final var variableSize = EntitySizeEstimator.estimateEntitySize(varEntity);

    final var requiredVariableRecordsForFlush =
        Math.ceil((double) (memoryLimit * 1024 * 1024) / variableSize);

    final var varHandler =
        getHandlers(config).stream()
            .filter(handler -> VariableHandler.class.isAssignableFrom(handler.getClass()))
            .findFirst()
            .orElseThrow();

    final List<String> varDocumentIds = new ArrayList<>();
    for (int i = 0; i < requiredVariableRecordsForFlush; i++) {
      final int finalI = i;
      final var variableRecord =
          factory.generateRecord(
              ValueType.VARIABLE,
              r ->
                  r.withValue(
                          ImmutableVariableRecordValue.builder()
                              .withValue(largeString)
                              .withScopeKey(finalI)
                              .build())
                      .withBrokerVersion("8.8.0"));
      exporter.export(variableRecord);

      varDocumentIds.add(varHandler.generateIds(variableRecord).getFirst());
    }

    clientAdapter.refresh();

    // then
    await()
        .untilAsserted(
            () -> {
              final var varDocs =
                  varDocumentIds.stream()
                      .map(
                          id -> {
                            try {
                              return clientAdapter.get(
                                  id, varHandler.getIndexName(), varHandler.getEntityType());
                            } catch (final IOException e) {
                              throw new RuntimeException(e);
                            }
                          })
                      .toList();

              assertThat(varDocs).isNotNull().isNotEmpty().doesNotContainNull();
            });
  }

  @TestTemplate
  void shouldNotFailWhenUpdatingBatchOperationAndListViewByScriptWithNoDocument(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    final Record record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_CHUNK,
            r ->
                r.withBrokerVersion("8.8.0")
                    .withIntent(BatchOperationChunkIntent.CREATED)
                    .withTimestamp(System.currentTimeMillis()));

    exporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    exporter.configure(exporterTestContext);
    exporter.open(new ExporterTestController());

    // act
    assertThatCode(() -> exporter.export(record)).doesNotThrowAnyException();
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

      camundaExporter.close();

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

      camundaExporter.close();
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
      camundaExporter.close();

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
      camundaExporter.close();

      // as the importer state is ignored, this should trigger a flush still resulting in the
      // record being visible in ES/OS
      controller.runScheduledTasks(Duration.ofSeconds(config.getBulk().getDelay()));

      // then
      assertThat(controller.getPosition()).isEqualTo(record.getPosition());
      verify(controller, atLeastOnce())
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

      final var camundaExporter = new CamundaExporter();
      // we don't want to stop on the SchemaManager checks
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

      camundaExporter.close();

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
