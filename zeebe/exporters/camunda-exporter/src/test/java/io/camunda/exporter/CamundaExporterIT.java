/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static io.camunda.exporter.utils.CamundaExporterITInvocationProvider.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Streams;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.AuthorizationHandler;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.DecisionHandler;
import io.camunda.exporter.handlers.DecisionRequirementsHandler;
import io.camunda.exporter.handlers.EventFromIncidentHandler;
import io.camunda.exporter.handlers.EventFromJobHandler;
import io.camunda.exporter.handlers.EventFromProcessInstanceHandler;
import io.camunda.exporter.handlers.EventFromProcessMessageSubscriptionHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceIncidentHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceProcessInstanceHandler;
import io.camunda.exporter.handlers.FormHandler;
import io.camunda.exporter.handlers.IncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromIncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewVariableFromVariableHandler;
import io.camunda.exporter.handlers.MetricFromProcessInstanceHandler;
import io.camunda.exporter.handlers.PostImporterQueueFromIncidentHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.handlers.SequenceFlowHandler;
import io.camunda.exporter.handlers.UserHandler;
import io.camunda.exporter.handlers.VariableHandler;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

    if (container.getDockerImageName().contains(ConnectionTypes.ELASTICSEARCH.getType())) {
      config.getConnect().setType(ConnectionTypes.ELASTICSEARCH.getType());
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
    provider.init(config);

    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  @Nested
  class ExportHandlerTests {
    final List<Entry<Class<?>, Function<String, ExportHandler<?, ?>>>> handlers =
        List.of(
            entry(UserIndex.class, UserHandler::new),
            entry(AuthorizationIndex.class, AuthorizationHandler::new),
            entry(DecisionIndex.class, DecisionHandler::new),
            entry(
                ListViewTemplate.class,
                (indexName) -> new ListViewFlowNodeFromIncidentHandler(indexName, false)),
            entry(
                ListViewTemplate.class,
                (indexName) -> new ListViewFlowNodeFromJobHandler(indexName, false)),
            entry(
                ListViewTemplate.class,
                (indexName) -> new ListViewFlowNodeFromProcessInstanceHandler(indexName, false)),
            entry(
                ListViewTemplate.class,
                (indexName) ->
                    new ListViewProcessInstanceFromProcessInstanceHandler(indexName, false)),
            entry(
                ListViewTemplate.class,
                (indexName) -> new ListViewVariableFromVariableHandler(indexName, false)),
            entry(VariableTemplate.class, (indexName) -> new VariableHandler(indexName, 8191)),
            entry(DecisionRequirementsIndex.class, DecisionRequirementsHandler::new),
            entry(PostImporterQueueTemplate.class, PostImporterQueueFromIncidentHandler::new),
            entry(FlowNodeInstanceTemplate.class, FlowNodeInstanceProcessInstanceHandler::new),
            entry(FlowNodeInstanceTemplate.class, FlowNodeInstanceIncidentHandler::new),
            entry(IncidentTemplate.class, (indexName) -> new IncidentHandler(indexName, false)),
            entry(SequenceFlowTemplate.class, SequenceFlowHandler::new),
            entry(DecisionInstanceTemplate.class, DecisionEvaluationHandler::new),
            entry(ProcessIndex.class, (indexName) -> new ProcessHandler(indexName, new XMLUtil())),
            entry(MetricIndex.class, MetricFromProcessInstanceHandler::new),
            entry(FormIndex.class, FormHandler::new),
            entry(
                EventTemplate.class, (indexName) -> new EventFromIncidentHandler(indexName, false)),
            entry(EventTemplate.class, (indexName) -> new EventFromJobHandler(indexName, false)),
            entry(
                EventTemplate.class,
                (indexName) -> new EventFromProcessInstanceHandler(indexName, false)),
            entry(
                EventTemplate.class,
                (indexName) -> new EventFromProcessMessageSubscriptionHandler(indexName, false)));

    @TestTemplate
    void shouldHandleExportedRecords(
        final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
        throws Exception {
      final var isElasticsearch =
          config.getConnect().getType().equals(ConnectionTypes.ELASTICSEARCH.getType());

      final var prefix = config.getIndex().getPrefix();
      final Set<IndexTemplateDescriptor> templates =
          Set.of(
              new ListViewTemplate(prefix, isElasticsearch),
              new FlowNodeInstanceTemplate(prefix, isElasticsearch),
              new VariableTemplate(prefix, isElasticsearch),
              new IncidentTemplate(prefix, isElasticsearch),
              new SequenceFlowTemplate(prefix, isElasticsearch),
              new PostImporterQueueTemplate(prefix, isElasticsearch),
              new DecisionInstanceTemplate(prefix, isElasticsearch),
              new EventTemplate(prefix, isElasticsearch));
      final Set<IndexDescriptor> indices =
          Set.of(
              new UserIndex(prefix, isElasticsearch),
              new AuthorizationIndex(prefix, isElasticsearch),
              new DecisionIndex(prefix, isElasticsearch),
              new DecisionRequirementsIndex(prefix, isElasticsearch),
              new ProcessIndex(prefix, isElasticsearch),
              new MetricIndex(prefix, isElasticsearch),
              new FormIndex(prefix, isElasticsearch));

      final var exporter = createExporter(indices, templates, config);

      for (final var getHandler : handlers) {
        final var handlerIndexType = getHandler.getKey();
        final var handlerDescriptor =
            Streams.concat(templates.stream(), indices.stream())
                .filter(handlerIndexType::isInstance)
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            String.format(
                                "Did not pass an descriptor of type [%s] to the exporter",
                                handlerIndexType.getSimpleName())));

        final var handler = getHandler.getValue().apply(handlerDescriptor.getFullQualifiedName());

        System.out.println("HANDLER = " + handler.getClass().getSimpleName());
        exportTest(exporter, handler, handlerDescriptor, clientAdapter);
      }
    }

    //    final var customHandlerRecords = Map.of(MetricFromProcessInstanceHandler.class, () ->
    // factory.generateRecord());

    private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> defaultRecordGenerator(
        final ExportHandler<S, T> handler) {
      Record<T> record =
          factory.generateRecord(
              handler.getHandledValueType(), r -> r.withTimestamp(System.currentTimeMillis()));
      var attempts = 1;
      // Sometimes the factory generates records with intents that are not handled by the
      // handler
      while (!handler.handlesRecord(record)) {
        record =
            factory.generateRecord(
                handler.getHandledValueType(), r -> r.withTimestamp(System.currentTimeMillis()));
        //         use a break an error to avoid endless loops
        if (++attempts >= 100) {
          throw new IllegalStateException(
              String.format(
                  "Could not generate a record which [%s] could handle after 100 attempts",
                  handler.getClass().getSimpleName()));
        }
      }
      return record;
    }

    private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> getHandlerRecord(
        final ExportHandler<S, T> handler) {
      //       when we generate records the .value.jobDeadline should be a valid time
      final Map<Class<?>, Supplier<Record<T>>> customRecordGenerators =
          Map.of(
              EventFromJobHandler.class,
              () -> {
                final var jobRecordValue =
                    ImmutableJobRecordValue.builder()
                        .from(factory.generateObject(JobRecordValue.class))
                        .withDeadline(System.currentTimeMillis() + 10000)
                        .build();
                final var record =
                    factory.generateRecord(
                        ValueType.JOB,
                        r ->
                            r.withValue(jobRecordValue)
                                .withTimestamp(System.currentTimeMillis())
                                .withIntent(JobIntent.CREATED));
                return (Record<T>) record;
              },
              ListViewFlowNodeFromJobHandler.class,
              () -> {
                final var jobRecordValue =
                    ImmutableJobRecordValue.builder()
                        .from(factory.generateObject(JobRecordValue.class))
                        .withDeadline(System.currentTimeMillis() + 10000)
                        .build();
                final var record =
                    factory.generateRecord(
                        ValueType.JOB,
                        r -> r.withValue(jobRecordValue).withTimestamp(System.currentTimeMillis()));
                return (Record<T>) record;
              },
              EventFromIncidentHandler.class,
              () -> {
                return factory.generateRecord(
                    ValueType.INCIDENT,
                    r ->
                        r.withTimestamp(System.currentTimeMillis())
                            .withIntent(IncidentIntent.RESOLVED));
              },
              DecisionEvaluationHandler.class,
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
                final Record<DecisionEvaluationRecordValue> record =
                    factory.generateRecord(
                        ValueType.DECISION_EVALUATION,
                        r ->
                            r.withValue(decisionRecordValue)
                                .withTimestamp(System.currentTimeMillis()));
                return (Record<T>) record;
              },
              MetricFromProcessInstanceHandler.class,
              () -> {
                final ProcessInstanceRecordValue processInstanceRecordValue =
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ProcessInstanceRecordValue.class))
                        .withParentProcessInstanceKey(-1L)
                        .build();
                final Record<ProcessInstanceRecordValue> processInstanceRecord =
                    factory.generateRecord(
                        ValueType.PROCESS_INSTANCE,
                        r ->
                            r.withIntent(ELEMENT_ACTIVATING)
                                .withValue(processInstanceRecordValue)
                                .withTimestamp(System.currentTimeMillis()));
                return (Record<T>) processInstanceRecord;
              });
      //      use a spy to modify the ones we need!!!

      return customRecordGenerators
          .getOrDefault(handler.getClass(), () -> defaultRecordGenerator(handler))
          .get();
    }

    private <S extends ExporterEntity<S>, T extends RecordValue> void exportTest(
        final CamundaExporter exporter,
        final ExportHandler<S, T> handler,
        final IndexDescriptor descriptor,
        final SearchClientAdapter clientAdapter)
        throws IOException {
      final var record = getHandlerRecord(handler);
      final var entityId = handler.generateIds(record).getFirst();
      final var expectedEntity = handler.createNewEntity(entityId);
      handler.updateEntity(record, expectedEntity);

      exporter.export(record);

      final var responseEntity =
          clientAdapter.get(
              expectedEntity.getId(), descriptor.getFullQualifiedName(), handler.getEntityType());

      assertThat(responseEntity)
          .describedAs(
              "Handler [%s] correctly handles a [%s] record",
              handler.getClass().getSimpleName(), handler.getHandledValueType())
          .isEqualTo(expectedEntity);
    }
  }
}
