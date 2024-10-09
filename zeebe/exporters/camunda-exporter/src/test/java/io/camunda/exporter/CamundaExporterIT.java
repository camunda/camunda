/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.schema.SchemaTestUtil.validateMappings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.entities.AuthorizationEntity;
import io.camunda.exporter.entities.UserEntity;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class CamundaExporterIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private final ExporterConfiguration config = new ExporterConfiguration();
  private final ExporterTestController controller = new ExporterTestController();
  private final ProtocolFactory factory = new ProtocolFactory();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  private ElasticsearchClient testClient;

  @BeforeAll
  public void beforeAll() {
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    config.getIndex().setPrefix("camunda-record");

    testClient = new ElasticsearchConnector(config.getConnect()).createClient();
  }

  @AfterAll
  void afterAll() throws IOException {
    testClient._transport().close();
  }

  @BeforeEach
  void beforeEach() throws IOException {
    config.getBulk().setSize(1); // force flushing on the first record
    testClient.indices().delete(req -> req.index("*"));
    testClient.indices().deleteIndexTemplate(req -> req.name("*"));

    config.setCreateSchema(false);
    config.getRetention().setEnabled(false);

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "template_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            config.getIndex().getPrefix() + "qualified_name",
            "alias",
            "index_name",
            "/mappings.json");
  }

  @Test
  void shouldPeriodicallyFlushBasedOnConfiguration() {
    // given
    final var duration = 2;
    config.getBulk().setDelay(duration);

    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

    exporter.configure(context);

    // when
    final var spiedController = spy(controller);
    exporter.open(spiedController);

    spiedController.runScheduledTasks(Duration.ofSeconds(duration));

    // then
    verify(spiedController, times(2)).scheduleCancellableTask(eq(Duration.ofSeconds(2)), any());
  }

  @Test
  void shouldUpdateExporterPositionAfterFlushing() {
    // given
    final var exporter = startExporter();

    // when
    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);
    assertThat(controller.getPosition()).isEqualTo(-1);

    exporter.export(record);

    // then
    assertThat(controller.getPosition()).isEqualTo(record.getPosition());
  }

  private Exporter startExporter() {
    final var exporter =
        new CamundaExporter(mockResourceProvider(Set.of(index), Set.of(indexTemplate)));

    final var context = getContext();
    exporter.configure(context);
    exporter.open(controller);

    return exporter;
  }

  private Map<PermissionType, List<String>> extractPermissions(
      final AuthorizationRecordValue record) {
    return record.getPermissions().stream()
        .collect(
            Collectors.toMap(
                AuthorizationRecordValue.PermissionValue::getPermissionType,
                AuthorizationRecordValue.PermissionValue::getResourceIds));
  }

  private Context getContext() {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
  }

  private ExporterResourceProvider mockResourceProvider(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors) {
    final var provider = mock(DefaultExporterResourceProvider.class, CALLS_REAL_METHODS);
    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  @Nested
  class RollingUpdateTests {
    @Test
    void shouldHaveCorrectSchemaUpdatesWithMultipleExporters() throws IOException {
      // given
      config.setCreateSchema(true);

      final var exporter1 = startExporter();
      final var exporter2 = startExporter();

      when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
      when(indexTemplate.getMappingsClasspathFilename())
          .thenReturn("/mappings-added-property.json");

      // when
      exporter1.open(controller);
      exporter2.open(controller);

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates = testClient.indices().getIndexTemplate(req -> req.name("*"));

      validateMappings(
          indices.result().get(index.getFullQualifiedName()).mappings(),
          "/mappings-added-property.json");
      validateMappings(
          indexTemplates.indexTemplates().stream()
              .filter(template -> template.name().equals(indexTemplate.getTemplateName()))
              .findFirst()
              .orElseThrow()
              .indexTemplate()
              .template()
              .mappings(),
          "/mappings-added-property.json");
    }

    @Test
    void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted() throws IOException {
      // given
      config.setCreateSchema(true);
      final var updatedExporter = startExporter();
      final var oldExporter = startExporter();

      // when
      when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
      when(indexTemplate.getMappingsClasspathFilename())
          .thenReturn("/mappings-added-property.json");

      updatedExporter.open(controller);

      when(index.getMappingsClasspathFilename()).thenReturn("/mappings.json");
      when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings.json");

      oldExporter.open(controller);

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates = testClient.indices().getIndexTemplate(req -> req.name("*"));

      validateMappings(
          indices.result().get(index.getFullQualifiedName()).mappings(),
          "/mappings-added-property.json");
      validateMappings(
          indexTemplates.indexTemplates().stream()
              .filter(template -> template.name().equals(indexTemplate.getTemplateName()))
              .findFirst()
              .orElseThrow()
              .indexTemplate()
              .template()
              .mappings(),
          "/mappings-added-property.json");
    }
  }

  @Nested
  class SchemaTests {
    @Test
    void shouldCreateAllSchemasIfCreateEnabled() throws IOException {
      // given
      config.setCreateSchema(true);
      startExporter();

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates =
          testClient.indices().getIndexTemplate(req -> req.name("template_name"));

      validateMappings(
          Objects.requireNonNull(indices.result().get(index.getFullQualifiedName()).mappings()),
          "/mappings.json");
      validateMappings(
          indexTemplates.indexTemplates().get(0).indexTemplate().template().mappings(),
          "/mappings.json");
    }

    @Test
    void shouldUpdateSchemasCorrectlyIfCreateEnabled() throws IOException {
      // given
      config.setCreateSchema(true);
      final var exporter = startExporter();

      // when
      when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
      when(indexTemplate.getMappingsClasspathFilename())
          .thenReturn("/mappings-added-property.json");

      exporter.open(controller);

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates =
          testClient.indices().getIndexTemplate(req -> req.name("template_name"));

      validateMappings(
          indices.result().get(index.getFullQualifiedName()).mappings(),
          "/mappings-added-property.json");
      validateMappings(
          indexTemplates.indexTemplates().getFirst().indexTemplate().template().mappings(),
          "/mappings-added-property.json");
    }

    @Test
    void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas() throws IOException {
      // given
      config.setCreateSchema(true);
      final var resourceProvider = mockResourceProvider(Set.of(index), Set.of(indexTemplate));
      final var exporter = new CamundaExporter(resourceProvider);
      final var context = getContext();
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var newIndex =
          SchemaTestUtil.mockIndex(
              "new_index_qualified", "new_alias", "new_index", "/mappings-added-property.json");
      final var newIndexTemplate =
          SchemaTestUtil.mockIndexTemplate(
              "new_template_name",
              "new_test*",
              "new_template_alias",
              Collections.emptyList(),
              "new_template_name",
              "/mappings-added-property.json");

      when(resourceProvider.getIndexDescriptors()).thenReturn(Set.of(index, newIndex));
      when(resourceProvider.getIndexTemplateDescriptors())
          .thenReturn(Set.of(indexTemplate, newIndexTemplate));

      exporter.open(controller);

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates = testClient.indices().getIndexTemplate(req -> req.name("*"));

      validateMappings(
          indices.result().get(newIndex.getFullQualifiedName()).mappings(),
          "/mappings-added-property.json");
      validateMappings(
          indexTemplates.indexTemplates().stream()
              .filter(template -> template.name().equals("new_template_name"))
              .findFirst()
              .orElseThrow()
              .indexTemplate()
              .template()
              .mappings(),
          "/mappings-added-property.json");
    }

    @Test
    void shouldNotPutAnySchemasIfCreatedDisabled() throws IOException {
      // given
      config.setCreateSchema(false);

      startExporter();

      // then
      final var indices = testClient.indices().get(req -> req.index("*"));
      final var indexTemplates =
          testClient.indices().getIndexTemplate(req -> req.name("template_name*"));

      assertThat(indices.result().size()).isEqualTo(0);
      assertThat(indexTemplates.indexTemplates().size()).isEqualTo(0);
    }

    @Test
    void shouldCreateLifeCyclePoliciesOnStartupIfEnabled() throws IOException {
      config.setCreateSchema(true);
      config.getRetention().setEnabled(true);
      config.getRetention().setPolicyName("policy_name");

      startExporter();

      final var policies = testClient.ilm().getLifecycle();

      assertThat(policies.get("policy_name")).isNotNull();
    }

    @Test
    void shouldNotCreateLifeCyclePoliciesIfDisabled() throws IOException {
      config.setCreateSchema(true);
      config.getRetention().setEnabled(false);
      config.getRetention().setPolicyName("not_created_policy");

      startExporter();
      final var policies = testClient.ilm().getLifecycle();

      assertThat(policies.get("not_created_policy")).isNull();
    }
  }

  @Nested
  class ExportTests {
    @Test
    void shouldExportUserRecord() throws IOException {
      // given
      final var exporter = startExporter();
      final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

      // when
      exporter.export(record);

      // then
      final String id = String.valueOf(record.getKey());
      final var response = testClient.get(b -> b.id(id).index("users"), UserEntity.class);
      assertThat(response)
          .extracting(GetResponse::index, GetResponse::id)
          .containsExactly("users", id);

      assertThat(response.source())
          .describedAs("User entity is updated correctly from the user record")
          .extracting(UserEntity::getEmail, UserEntity::getName, UserEntity::getUsername)
          .containsExactly(
              record.getValue().getEmail(),
              record.getValue().getName(),
              record.getValue().getUsername());
    }

    @Test
    void shouldExportAuthorizationRecord() throws IOException {
      // given
      final var context = getContext();
      final var exporter = new CamundaExporter();
      exporter.configure(context);
      exporter.open(controller);

      final Record<AuthorizationRecordValue> record =
          factory.generateRecord(ValueType.AUTHORIZATION);

      // when
      exporter.export(record);

      // then
      final String id = String.valueOf(record.getKey());
      final var response =
          testClient.get(b -> b.id(id).index("authorizations"), AuthorizationEntity.class);
      assertThat(response)
          .extracting(GetResponse::index, GetResponse::id)
          .containsExactly("authorizations", id);

      assertThat(response.source())
          .describedAs("Authorization entity is updated correctly from the authorization record")
          .extracting(
              AuthorizationEntity::getOwnerKey,
              AuthorizationEntity::getOwnerType,
              AuthorizationEntity::getResourceType,
              AuthorizationEntity::getPermissionValues)
          .containsExactly(
              record.getValue().getOwnerKey(),
              record.getValue().getOwnerType(),
              record.getValue().getResourceType(),
              extractPermissions(record.getValue()));
    }

    @Test
    void shouldExportRecordOnceBulkSizeReached() {
      // given
      config.getBulk().setSize(2);
      final var exporter = new CamundaExporter();

      final var context = getContext();
      exporter.configure(context);
      final var controllerSpy = spy(controller);
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

    @Test
    void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater()
        throws IOException {
      // given
      final var exporter = new CamundaExporter();

      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

      exporter.configure(context);
      exporter.open(controller);

      // when
      final var currentPort = CONTAINER.getFirstMappedPort();
      CONTAINER.stop();
      Awaitility.await().until(() -> !CONTAINER.isRunning());

      final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

      Assertions.assertThatThrownBy(() -> exporter.export(record))
          .isInstanceOf(ExporterException.class)
          .hasMessageContaining("Connection refused");

      // starts the container on the same port again
      CONTAINER
          .withEnv("discovery.type", "single-node")
          .withExposedPorts(9200)
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.withHostConfig(
                      new HostConfig()
                          .withPortBindings(
                              new PortBinding(
                                  Ports.Binding.bindPort(currentPort), new ExposedPort(9200)))))
          .start();
      Awaitility.await().until(CONTAINER::isRunning);

      final Record<UserRecordValue> record2 = factory.generateRecord(ValueType.USER);
      exporter.export(record2);

      // then
      final var testClient = new ElasticsearchConnector(config.getConnect()).createClient();

      Awaitility.await()
          .until(
              () ->
                  testClient.search(s -> s.index("users"), Map.class).hits().total().value() == 2L);

      final var documents =
          testClient.search(s -> s.index("users"), Map.class).hits().hits().stream()
              .filter(hit -> hit.id().equals(String.valueOf(record.getKey())))
              .toList();
      assertThat(documents).hasSize(1);
    }
  }
}
