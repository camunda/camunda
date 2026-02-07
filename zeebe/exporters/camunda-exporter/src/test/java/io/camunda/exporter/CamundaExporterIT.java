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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.ExporterEntity;
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
import io.camunda.zeebe.util.ObjectSizeEstimator;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
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
    await().until(() -> !container.isRunning());

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
        new ExporterTestContext(),
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
        new ExporterTestContext(),
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
                    .withIntent(IncidentIntent.CREATED)
                    .withTimestamp(invalidTimestamp));
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new ExporterTestContext(),
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
  void shouldFailToOpenWhenSchemaMissingThenOpenAfterSchemaCreationAndExport(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // Do not create schema yet
    final var exporter = spy(new CamundaExporter());
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
        new ExporterTestContext(),
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
    final var exporter = new CamundaExporter();

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
    final var variableSize = ObjectSizeEstimator.estimateSize(varEntity);

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

    final CamundaExporter camundaExporter = new CamundaExporter();
    final ExporterTestContext exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));

    camundaExporter.configure(exporterTestContext);
    camundaExporter.open(new ExporterTestController());

    // act
    assertThatCode(() -> camundaExporter.export(record)).doesNotThrowAnyException();
  }
}
