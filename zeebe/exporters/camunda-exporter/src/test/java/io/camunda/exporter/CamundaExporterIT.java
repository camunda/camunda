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
import static io.camunda.exporter.utils.CamundaExporterITInvocationProvider.CONFIG_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.schema.MappingSource;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.ImportValueTypes;
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
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(CamundaExporterITInvocationProvider.class)
final class CamundaExporterIT {

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
            CONFIG_PREFIX + "-template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            CONFIG_PREFIX + "-qualified_name", "alias", "index_name", "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CONFIG_PREFIX + "-template_index_qualified_name");
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
    final var record = generateRecordWithSupportedBrokerVersion(ValueType.AUTHORIZATION);
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
    final var record = generateRecordWithSupportedBrokerVersion(ValueType.AUTHORIZATION);
    final var record2 = generateRecordWithSupportedBrokerVersion(ValueType.AUTHORIZATION);

    exporter.export(record);
    exporter.export(record2);
    // then
    verify(controllerSpy, never())
        .updateLastExportedRecordPosition(eq(record.getPosition()), any());
    verify(controllerSpy).updateLastExportedRecordPosition(eq(record2.getPosition()), any());
  }

  @ParameterizedTest
  @MethodSource("containerProvider")
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

    final var record = generateRecordWithSupportedBrokerVersion(ValueType.AUTHORIZATION);

    assertThatThrownBy(() -> exporter.export(record))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("Connection refused");

    // starts the container on the same port again
    container
        .withEnv("discovery.type", "single-node")
        .setPortBindings(List.of(currentPort + ":9200"));
    container.start();

    final var record2 = generateRecordWithSupportedBrokerVersion(ValueType.AUTHORIZATION);
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
    final CamundaExporter camundaExporter = new CamundaExporter();
    camundaExporter.configure(getContextFromConfig(config));

    final var adapter = ClientAdapter.of(config);
    final var mappingsBeforeOpen =
        adapter.getSearchEngineClient().getMappings(CONFIG_PREFIX + "*", MappingSource.INDEX);
    assertThat(mappingsBeforeOpen.keySet()).isEmpty();

    // when
    camundaExporter.open(new ExporterTestController());

    // then
    final var mappingsAfterOpen =
        adapter.getSearchEngineClient().getMappings(CONFIG_PREFIX + "*", MappingSource.INDEX);
    assertThat(mappingsAfterOpen.keySet())
        // we verify the names hard coded on purpose
        // to make sure no index will be accidentally dropped, names are changed or added
        .containsExactlyInAnyOrder(
            "custom-prefix-camunda-authorization-8.7.0_",
            "custom-prefix-camunda-group-8.7.0_",
            "custom-prefix-camunda-mapping-8.7.0_",
            "custom-prefix-camunda-role-8.7.0_",
            "custom-prefix-camunda-tenant-8.7.0_",
            "custom-prefix-camunda-user-8.7.0_",
            "custom-prefix-camunda-web-session-8.7.0_",
            "custom-prefix-operate-batch-operation-1.0.0_",
            "custom-prefix-operate-decision-8.3.0_",
            "custom-prefix-operate-decision-instance-8.3.0_",
            "custom-prefix-operate-decision-requirements-8.3.0_",
            "custom-prefix-operate-event-8.3.0_",
            "custom-prefix-operate-flownode-instance-8.3.1_",
            "custom-prefix-operate-import-position-8.3.0_",
            "custom-prefix-operate-incident-8.3.1_",
            "custom-prefix-operate-list-view-8.3.0_",
            "custom-prefix-operate-metric-8.3.0_",
            "custom-prefix-operate-operation-8.4.1_",
            "custom-prefix-operate-post-importer-queue-8.3.0_",
            "custom-prefix-operate-process-8.3.0_",
            "custom-prefix-operate-sequence-flow-8.3.0_",
            "custom-prefix-operate-variable-8.3.0_",
            "custom-prefix-operate-job-8.6.0_",
            "custom-prefix-tasklist-draft-task-variable-8.3.0_",
            "custom-prefix-tasklist-form-8.4.0_",
            "custom-prefix-tasklist-metric-8.3.0_",
            "custom-prefix-tasklist-task-8.5.0_",
            "custom-prefix-tasklist-task-variable-8.3.0_");
  }

  @TestTemplate
  void shouldExportRecord(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    // given
    final var valueType = ValueType.VARIABLE;
    final Record record = generateRecordWithSupportedBrokerVersion(valueType);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config, mock(ExporterEntityCacheProvider.class), new SimpleMeterRegistry());
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
  void shouldNotExport860RecordButStillUpdateLastExportedPosition(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    final var recordPosition = 123456789L;
    final var record =
        factory.generateRecord(
            ValueType.AUTHORIZATION,
            r -> r.withBrokerVersion("8.6.0").withPosition(recordPosition));

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
        config, mock(ExporterEntityCacheProvider.class), new SimpleMeterRegistry());

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

  private Record<?> generateRecordWithSupportedBrokerVersion(final ValueType valueType) {
    return factory.generateRecord(valueType, r -> r.withBrokerVersion("8.7.0"));
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

    config.getArchiver().setRolloverEnabled(false);
    return config;
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config));
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
    provider.init(config, mock(ExporterEntityCacheProvider.class), new SimpleMeterRegistry());

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
        new ImportPositionIndex(CONFIG_PREFIX, true).getFullQualifiedName();

    @BeforeEach
    void setup() {
      controller.resetScheduledTasks();
    }

    @TestTemplate
    void shouldNotFlushIfImportersAreNotCompleted(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws IOException {
      // given
      final var context = spy(getContextFromConfig(config));
      doReturn(partitionId).when(context).getPartitionId();
      camundaExporter.configure(context);
      camundaExporter.open(controller);

      // when

      // adds a not complete position index document so exporter sees importing as not yet completed
      indexImportPositionEntity(ImportValueTypes.DECISION, false, clientAdapter);
      clientAdapter.refresh();

      controller.runScheduledTasks(Duration.ofMinutes(1));

      final var record =
          factory.generateRecord(
              ValueType.AUTHORIZATION,
              r -> r.withBrokerVersion("8.7.0").withTimestamp(System.currentTimeMillis()));

      camundaExporter.export(record);

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

      // mark all import position documents as completed which signals all record readers as
      // completed
      for (final var type : ImportValueTypes.values()) {
        indexImportPositionEntity(type, true, clientAdapter);
      }

      controller.runScheduledTasks(Duration.ofMinutes(1));

      // when
      final var record =
          factory.generateRecord(
              ValueType.AUTHORIZATION,
              r -> r.withBrokerVersion("8.7.0").withTimestamp(System.currentTimeMillis()));

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

    private void indexImportPositionEntity(
        final ImportValueTypes type, final boolean completed, final SearchClientAdapter client)
        throws IOException {
      final var entity =
          new ImportPositionEntity()
              .setPartitionId(partitionId)
              .setAliasName(type.getAliasTemplate())
              .setCompleted(completed);

      client.index(entity.getId(), importPositionIndexName, entity);
    }
  }
}
