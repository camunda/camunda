/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.schema.SchemaTestUtil.getElsIndexAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getElsIndexTemplateAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getOpensearchIndexAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getOpensearchIndexTemplateAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static io.camunda.exporter.schema.SchemaTestUtil.validateMappings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.webapps.schema.entities.usermanagement.Permission;
import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
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
  private static final ElasticsearchContainer CONTAINER =
      TestSupport.createDefeaultElasticsearchContainer();

  private static final ExporterConfiguration config = new ExporterConfiguration();
  private final ExporterTestController controller = new ExporterTestController();
  private final ProtocolFactory factory = new ProtocolFactory();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  private ElasticsearchClient testClient;

  @BeforeAll
  public void beforeAll() {
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());

    testClient = new ElasticsearchConnector(config.getConnect()).createClient();
  }

  @AfterAll
  void afterAll() throws IOException {
    testClient._transport().close();
  }

  @BeforeEach
  void beforeEach() throws IOException {
    config.getConnect().setType("elasticsearch");
    config.getIndex().setPrefix("camunda-record");
    config.getIndex().setNumberOfShards(1);
    config.getIndex().setNumberOfReplicas(0);

    config.getBulk().setSize(1); // force flushing on the first record
    testClient.indices().delete(req -> req.index("*"));
    testClient.indices().deleteIndexTemplate(req -> req.name("*"));

    config.setCreateSchema(false);
    config.getRetention().setEnabled(false);
    config.getRetention().setMinimumAge("30d");

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
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

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(config.getIndex().getPrefix() + "template_index_qualified_name");
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

  private List<Permission> extractPermissions(final AuthorizationRecordValue record) {
    return record.getPermissions().stream()
        .map(
            permissionValue ->
                new Permission(
                    permissionValue.getPermissionType().name(), permissionValue.getResourceIds()))
        .collect(Collectors.toList());
  }

  private Context getContext() {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
  }

  private ExporterResourceProvider mockResourceProvider(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors) {
    final var provider = mock(DefaultExporterResourceProvider.class, CALLS_REAL_METHODS);
    provider.init(config);
    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  @Test
  void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted() throws IOException {
    // given
    config.setCreateSchema(true);
    final var updatedExporter = startExporter();
    final var oldExporter = startExporter();

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

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

  void shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
      final Callable<JsonNode> getIndex, final Callable<JsonNode> getTemplate) throws Exception {
    // given
    config.setCreateSchema(true);

    final var exporter1 = startExporter();
    final var exporter2 = startExporter();

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    // when
    exporter1.open(controller);
    exporter2.open(controller);

    // then
    final var retrievedIndex = getIndex.call();
    final var retrievedIndexTemplate = getTemplate.call();

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted(
      final Callable<JsonNode> getIndex, final Callable<JsonNode> getTemplate) throws Exception {
    // given
    config.setCreateSchema(true);
    final var updatedExporter = startExporter();
    final var oldExporter = startExporter();

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    updatedExporter.open(controller);

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings.json");

    oldExporter.open(controller);

    // then
    final var retrievedIndex = getIndex.call();
    final var retrievedIndexTemplate = getTemplate.call();

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @Nested
  class ElasticsearchBackedExporter {
    @Container
    private static final ElasticsearchContainer CONTAINER =
        TestSupport.createDefeaultElasticsearchContainer();

    private static ElasticsearchClient client;

    @BeforeEach
    public void beforeEach() throws IOException {
      client.indices().delete(req -> req.index("*"));
      client.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      config.getConnect().setType("elasticsearch");
      client = new ElasticsearchConnector(config.getConnect()).createClient();
    }

    @Test
    void shouldHaveCorrectSchemaUpdatesWithMultipleExporters() throws Exception {
      CamundaExporterIT.this.shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), client),
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), client));
    }

    @Test
    void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted() throws Exception {
      CamundaExporterIT.this.shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), client),
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), client));
    }
  }

  @Nested
  class OpensearchBackedExporter {
    @Container
    private static final OpensearchContainer<?> CONTAINER =
        TestSupport.createDefaultOpensearchContainer();

    private static OpenSearchClient client;

    @BeforeEach
    public void beforeEach() throws IOException {
      client.indices().delete(req -> req.index(config.getIndex().getPrefix() + "*"));
      client.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      config.getConnect().setType("opensearch");
      client = new OpensearchConnector(config.getConnect()).createClient();
    }

    @Test
    void shouldHaveCorrectSchemaUpdatesWithMultipleExporters() throws Exception {
      CamundaExporterIT.this.shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), client),
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), client));
    }

    @Test
    void shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted() throws Exception {
      CamundaExporterIT.this.shouldNotErrorIfOldExporterRestartsWhileNewExporterHasAlreadyStarted(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), client),
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), client));
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
              AuthorizationEntity::getPermissions)
          .containsExactlyInAnyOrder(
              record.getValue().getOwnerKey(),
              record.getValue().getOwnerType().name(),
              record.getValue().getResourceType().name(),
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
          .setPortBindings(List.of(currentPort + ":9200"));
      CONTAINER.start();

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

      CONTAINER.setPortBindings(List.of());
    }
  }

  @Nested
  final class ConfigValidationTest {
    @Test
    void shouldRejectWrongConnectionType() {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

      config.getConnect().setType("mysql");

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @Test
    void shouldNotAllowUnderscoreInIndexPrefix() {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

      config.getIndex().setPrefix("i_am_invalid");

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-1, 0})
    void shouldForbidNonPositiveNumberOfShards(final int invalidNumberOfShards) {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

      config.getIndex().setNumberOfShards(invalidNumberOfShards);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"1", "-1", "1ms"})
    void shouldNotAllowInvalidMinimumAge(final String invalidMinAge) {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
      config.getRetention().setMinimumAge(invalidMinAge);

      // when - then
      assertThatCode(() -> exporter.configure(context))
          .isInstanceOf(ExporterException.class)
          .hasMessageContaining("must match pattern '^[0-9]+[dhms]$'")
          .hasMessageContaining("minimumAge '" + invalidMinAge + "'");
    }

    @Test
    void shouldForbidNegativeNumberOfReplicas() {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
      config.getIndex().setNumberOfReplicas(-1);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }
  }
}
