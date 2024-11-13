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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
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
    final Record<UserRecordValue> record = factory.generateRecord(ValueType.AUTHORIZATION);
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
    final var record = factory.generateRecord(ValueType.AUTHORIZATION);
    final var record2 = factory.generateRecord(ValueType.AUTHORIZATION);

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

    final Record<UserRecordValue> record = factory.generateRecord(ValueType.AUTHORIZATION);

    assertThatThrownBy(() -> exporter.export(record))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("Connection refused");

    // starts the container on the same port again
    container
        .withEnv("discovery.type", "single-node")
        .setPortBindings(List.of(currentPort + ":9200"));
    container.start();

    final Record<UserRecordValue> record2 = factory.generateRecord(ValueType.AUTHORIZATION);
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
    provider.init(config, mock(ExporterEntityCacheProvider.class));

    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }
}
