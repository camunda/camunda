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
import static io.camunda.exporter.utils.CamundaExporterITInvocationProvider.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.cache.ProcessCacheLoaderFactory;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.EventFromJobHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.MetricFromProcessInstanceHandler;
import io.camunda.exporter.handlers.UserTaskProcessInstanceHandler;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.CamundaExporterHandlerITInvocationProvider;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
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
import org.mockito.Mockito;
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
    final var exporter = new CamundaExporter(mockResourceProvider(Set.of(), Set.of(), config));

    final var context = getContextFromConfig(config);
    exporter.configure(context);

    final var exporterController = new ExporterTestController();
    exporter.open(exporterController);

    // when
    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);
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
    final var exporter = new CamundaExporter(mockResourceProvider(Set.of(), Set.of(), config));

    final var context = getContextFromConfig(config);
    exporter.configure(context);
    final var controllerSpy = spy(new ExporterTestController());
    exporter.open(controllerSpy);

    // when
    final var record = factory.generateRecord(ValueType.USER);
    final var record2 = factory.generateRecord(ValueType.USER);

    exporter.export(record);
    exporter.export(record2);
    // then
    verify(controllerSpy, never()).updateLastExportedRecordPosition(record.getPosition());
    verify(controllerSpy).updateLastExportedRecordPosition(record2.getPosition());
  }

  @ParameterizedTest
  @MethodSource("containerProvider")
  void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater(
      final GenericContainer<?> container) {
    // given
    final var config = getConnectConfigForContainer(container);
    final var exporter = new CamundaExporter(mockResourceProvider(Set.of(), Set.of(), config));

    final var context = getContextFromConfig(config);
    final ExporterTestController controller = Mockito.spy(new ExporterTestController());

    exporter.configure(context);
    exporter.open(controller);

    // when
    final var currentPort = container.getFirstMappedPort();
    container.stop();
    Awaitility.await().until(() -> !container.isRunning());

    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

    assertThatThrownBy(() -> exporter.export(record))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("Connection refused");

    // starts the container on the same port again
    container
        .withEnv("discovery.type", "single-node")
        .setPortBindings(List.of(currentPort + ":9200"));
    container.start();

    final Record<UserRecordValue> record2 = factory.generateRecord(ValueType.USER);
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

    final var exporter = createExporter(Set.of(), Set.of(), config);

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

  private static Stream<Arguments> containerProvider() {
    return Stream.of(
        Arguments.of(TestSupport.createDefeaultElasticsearchContainer()),
        Arguments.of(TestSupport.createDefaultOpensearchContainer()));
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
    provider.init(config, mock(ProcessCacheLoaderFactory.class));

    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  @Nested
  @ExtendWith(CamundaExporterHandlerITInvocationProvider.class)
  class ExportHandlerTests {

    final Map<Class<?>, Function<ExportHandler<?, ?>, Record<?>>> customRecordGenerators =
        new HashMap<>(
            Map.of(
                EventFromJobHandler.class,
                this::jobRecordGenerator,
                ListViewFlowNodeFromJobHandler.class,
                this::jobRecordGenerator,
                ListViewProcessInstanceFromProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS),
                ListViewFlowNodeFromProcessInstanceHandler.class,
                (handler) ->
                    processInstanceRecordGenerator(handler, BpmnElementType.BOUNDARY_EVENT),
                DecisionEvaluationHandler.class,
                this::decisionEvalRecordGenerator,
                MetricFromProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS),
                UserTaskProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS)));

    @TestTemplate
    @SuppressWarnings("unchecked")
    <S extends ExporterEntity<S>, T extends RecordValue> void allHandlerTestsWithInvocationProvider(
        final CamundaExporter exporter,
        final SearchClientAdapter clientAdapter,
        final ExportHandler<S, T> handler)
        throws IOException {

      final var currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
      try (final var mocked = mockStatic(OffsetDateTime.class)) {
        mocked.when(OffsetDateTime::now).thenReturn(currentTime);

        final var record =
            (Record<T>)
                customRecordGenerators
                    .getOrDefault(handler.getClass(), this::defaultRecordGenerator)
                    .apply(handler);
        customRecordGenerators.put(handler.getClass(), (ignored) -> record);

        final var entityId = handler.generateIds(record).getFirst();
        final var expectedEntity = handler.createNewEntity(entityId);
        handler.updateEntity(record, expectedEntity);
        exporter.export(record);

        final var responseEntity =
            clientAdapter.get(
                expectedEntity.getId(), handler.getIndexName(), handler.getEntityType());

        assertThat(responseEntity)
            .describedAs(
                "Handler [%s] correctly handles a [%s] record",
                handler.getClass().getSimpleName(), handler.getHandledValueType())
            .isEqualTo(expectedEntity);
      }
    }

    private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> recordGenerator(
        final ExportHandler<S, T> handler, final Supplier<Record<T>> createRecord) {
      // Sometimes the factory generates record with intents that are not supported by the handler.
      var record = createRecord.get();
      do {
        record = createRecord.get();
      } while (!handler.handlesRecord(record));
      return record;
    }

    private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> jobRecordGenerator(
        final ExportHandler<S, T> handler) {
      return recordGenerator(
          handler,
          () -> {
            final var jobRecordValue =
                ImmutableJobRecordValue.builder()
                    .from(factory.generateObject(JobRecordValue.class))
                    .withDeadline(System.currentTimeMillis() + 10000)
                    .build();
            return factory.generateRecord(
                ValueType.JOB,
                r ->
                    r.withValue((T) jobRecordValue)
                        .withTimestamp(System.currentTimeMillis())
                        .withIntent(JobIntent.CREATED));
          });
    }

    private <S extends ExporterEntity<S>, T extends RecordValue>
        Record<T> decisionEvalRecordGenerator(final ExportHandler<S, T> handler) {
      return recordGenerator(
          handler,
          () -> {
            final ImmutableEvaluatedDecisionValue decisionValue =
                ImmutableEvaluatedDecisionValue.builder()
                    .from(factory.generateObject(EvaluatedDecisionValue.class))
                    .build();
            final DecisionEvaluationRecordValue decisionRecordValue =
                ImmutableDecisionEvaluationRecordValue.builder()
                    .from(factory.generateObject(DecisionEvaluationRecordValue.class))
                    .withEvaluatedDecisions(List.of(decisionValue))
                    .build();
            return factory.generateRecord(
                ValueType.DECISION_EVALUATION,
                r ->
                    r.withValue((T) decisionRecordValue).withTimestamp(System.currentTimeMillis()));
          });
    }

    private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> defaultRecordGenerator(
        final ExportHandler<S, T> handler) {
      return recordGenerator(
          handler,
          () ->
              factory.generateRecord(
                  handler.getHandledValueType(), r -> r.withTimestamp(System.currentTimeMillis())));
    }

    private <S extends ExporterEntity<S>, T extends RecordValue>
        Record<T> processInstanceRecordGenerator(
            final ExportHandler<S, T> handler, final BpmnElementType elementType) {
      return recordGenerator(
          handler,
          () -> {
            final ProcessInstanceRecordValue processInstanceRecordValue =
                ImmutableProcessInstanceRecordValue.builder()
                    .from(factory.generateObject(ProcessInstanceRecordValue.class))
                    .withParentProcessInstanceKey(-1L)
                    .withBpmnElementType(elementType)
                    .build();
            return factory.generateRecord(
                ValueType.PROCESS_INSTANCE,
                r ->
                    r.withIntent(ELEMENT_ACTIVATING)
                        .withValue((T) processInstanceRecordValue)
                        .withTimestamp(System.currentTimeMillis()));
          });
    }
  }
}
