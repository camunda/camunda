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
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
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
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class CamundaExporterIT {

  private static final ExporterConfiguration CONFIG = new ExporterConfiguration();
  private final ExporterTestController controller = new ExporterTestController();
  private final ProtocolFactory factory = new ProtocolFactory();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  @BeforeEach
  void beforeEach() throws IOException {
    CONFIG.getIndex().setPrefix("camunda-record");
    CONFIG.getIndex().setNumberOfShards(1);
    CONFIG.getIndex().setNumberOfReplicas(0);

    CONFIG.getBulk().setSize(1); // force flushing on the first record

    CONFIG.setCreateSchema(false);
    CONFIG.getRetention().setEnabled(false);
    CONFIG.getRetention().setMinimumAge("30d");

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            CONFIG.getIndex().getPrefix() + "template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            CONFIG.getIndex().getPrefix() + "qualified_name",
            "alias",
            "index_name",
            "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CONFIG.getIndex().getPrefix() + "template_index_qualified_name");
  }

  @Test
  void shouldPeriodicallyFlushBasedOnConfiguration() {
    // given
    final var duration = 2;
    CONFIG.getBulk().setDelay(duration);

    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

    exporter.configure(context);

    // when
    final var spiedController = spy(controller);
    exporter.open(spiedController);

    spiedController.runScheduledTasks(Duration.ofSeconds(duration));

    // then
    verify(spiedController, times(2)).scheduleCancellableTask(eq(Duration.ofSeconds(2)), any());
  }

  void shouldUpdateExporterPositionAfterFlushing() {
    // given
    final var exporter = new CamundaExporter(mockResourceProvider(Set.of(), Set.of()));

    final var context = getContext();
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
        .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));
  }

  private ExporterResourceProvider mockResourceProvider(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors) {
    final var provider = mock(DefaultExporterResourceProvider.class, CALLS_REAL_METHODS);
    provider.init(CONFIG);
    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }

  void shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
      final Callable<JsonNode> getIndex, final Callable<JsonNode> getTemplate) throws Exception {
    // given
    CONFIG.setCreateSchema(true);

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
    CONFIG.setCreateSchema(true);
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

  void shouldExportRoleRecord(
      final Callable<RoleEntity> getResponse, final Record<RoleRecordValue> record)
      throws Exception {
    // given
    CONFIG.setCreateSchema(true);
    final var exporter = startExporter();

    // when
    exporter.export(record);

    // then
    final var responseRoleEntity = getResponse.call();

    assertThat(responseRoleEntity)
        .describedAs("User entity is updated correctly from the user record")
        .extracting(
            RoleEntity::getRoleKey,
            RoleEntity::getName,
            RoleEntity::getEntityKey,
            RoleEntity::getId)
        .containsExactly(
            record.getValue().getRoleKey(),
            record.getValue().getName(),
            record.getValue().getEntityKey(),
            String.valueOf(record.getKey()));
  }

  void shouldExportUserRecord(
      final Callable<UserEntity> getResponse, final Record<UserRecordValue> record)
      throws Exception {
    // given
    CONFIG.setCreateSchema(true);
    final var exporter = startExporter();

    // when
    exporter.export(record);

    // then
    final var responseUserEntity = getResponse.call();

    assertThat(responseUserEntity)
        .describedAs("User entity is updated correctly from the user record")
        .extracting(
            UserEntity::getEmail, UserEntity::getName, UserEntity::getUsername, UserEntity::getId)
        .containsExactly(
            record.getValue().getEmail(),
            record.getValue().getName(),
            record.getValue().getUsername(),
            String.valueOf(record.getKey()));
  }

  void shouldExportAuthorizationRecord(
      final Callable<AuthorizationEntity> getResponse,
      final Record<AuthorizationRecordValue> record)
      throws Exception {
    // given
    CONFIG.setCreateSchema(true);
    final var exporter = startExporter();

    // when
    exporter.export(record);

    // then
    final var responseAuthorizationEntity = getResponse.call();

    assertThat(responseAuthorizationEntity)
        .describedAs("Authorization entity is updated correctly from the authorization record")
        .extracting(
            AuthorizationEntity::getOwnerKey,
            AuthorizationEntity::getOwnerType,
            AuthorizationEntity::getResourceType,
            AuthorizationEntity::getPermissions,
            AuthorizationEntity::getId)
        .containsExactly(
            record.getValue().getOwnerKey(),
            String.valueOf(record.getValue().getOwnerType()),
            String.valueOf(record.getValue().getResourceType()),
            extractPermissions(record.getValue()),
            String.valueOf(record.getKey()));
  }

  void shouldExportRecordOnceBulkSizeReached() {
    // given
    CONFIG.getBulk().setSize(2);
    CONFIG.setCreateSchema(true);
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

  void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater(
      final GenericContainer<?> container, final Callable<Long> getTotalUsers) {
    // given
    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>(CONFIG.getConnect().getType(), CONFIG));

    exporter.configure(context);
    exporter.open(controller);

    // when
    final var currentPort = container.getFirstMappedPort();
    container.stop();
    Awaitility.await().until(() -> !container.isRunning());

    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

    Assertions.assertThatThrownBy(() -> exporter.export(record))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("Connection refused");

    // starts the container on the same port again
    container
        .withEnv("discovery.type", "single-node")
        .setPortBindings(List.of(currentPort + ":9200"));
    container.start();

    final Record<UserRecordValue> record2 = factory.generateRecord(ValueType.USER);
    exporter.export(record2);

    Awaitility.await().until(() -> getTotalUsers.call() == 2L);

    container.setPortBindings(List.of());
  }

  @Nested
  @Testcontainers
  class ElasticsearchBackedExporter {
    @Container
    private static final ElasticsearchContainer CONTAINER =
        TestSupport.createDefeaultElasticsearchContainer();

    private static ElasticsearchClient client;
    private final ProtocolFactory factory = new ProtocolFactory();

    @BeforeEach
    public void beforeEach() throws IOException {
      client.indices().delete(req -> req.index("*"));
      client.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      CONFIG.getConnect().setType("elasticsearch");
      client = new ElasticsearchConnector(CONFIG.getConnect()).createClient();
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

    @Test
    void shouldExportUserRecord() throws Exception {
      final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

      final var recordId = String.valueOf(record.getKey());

      CamundaExporterIT.this.shouldExportUserRecord(
          () ->
              client
                  .get(
                      b -> b.id(recordId).index("camunda-record-identity-users-8.7.0_"),
                      UserEntity.class)
                  .source(),
          record);
    }

    @Test
    void shouldExportAuthorizationRecord() throws Exception {
      final Record<AuthorizationRecordValue> record =
          factory.generateRecord(ValueType.AUTHORIZATION);
      final var recordId = String.valueOf(record.getKey());

      CamundaExporterIT.this.shouldExportAuthorizationRecord(
          () ->
              client
                  .get(
                      b -> b.id(recordId).index("camunda-record-identity-authorizations-8.7.0_"),
                      AuthorizationEntity.class)
                  .source(),
          record);
    }

    @Test
    void shouldExportRecordOnceBulkSizeReached() {
      CamundaExporterIT.this.shouldExportRecordOnceBulkSizeReached();
    }

    @Test
    void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater() {
      CamundaExporterIT.this
          .shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater(
              CONTAINER,
              () ->
                  client
                      .search(
                          s -> s.index("camunda-record-identity-users-8.7.0_"), UserEntity.class)
                      .hits()
                      .total()
                      .value());
    }

    @Test
    void shouldUpdateExporterPositionAfterFlushing() {
      CamundaExporterIT.this.shouldUpdateExporterPositionAfterFlushing();
    }
  }

  @Nested
  @Testcontainers
  class OpensearchBackedExporter {
    @Container
    private static final OpensearchContainer<?> CONTAINER =
        TestSupport.createDefaultOpensearchContainer();

    private static OpenSearchClient client;
    private final ProtocolFactory factory = new ProtocolFactory();

    @BeforeEach
    public void beforeEach() throws IOException {
      client.indices().delete(req -> req.index(CONFIG.getIndex().getPrefix() + "*"));
      client.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      CONFIG.getConnect().setType("opensearch");
      client = new OpensearchConnector(CONFIG.getConnect()).createClient();
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

    @Test
    void shouldExportUserRecord() throws Exception {
      final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);
      final var recordId = String.valueOf(record.getKey());

      CamundaExporterIT.this.shouldExportUserRecord(
          () ->
              client
                  .get(
                      b -> b.id(recordId).index("camunda-record-identity-users-8.7.0_"),
                      UserEntity.class)
                  .source(),
          record);
    }

    @Test
    void shouldExportAuthorizationRecord() throws Exception {
      final Record<AuthorizationRecordValue> record =
          factory.generateRecord(ValueType.AUTHORIZATION);
      final var recordId = String.valueOf(record.getKey());

      CamundaExporterIT.this.shouldExportAuthorizationRecord(
          () ->
              client
                  .get(
                      b -> b.id(recordId).index("camunda-record-identity-authorizations-8.7.0_"),
                      AuthorizationEntity.class)
                  .source(),
          record);
    }

    @Test
    void shouldExportRecordOnceBulkSizeReached() {
      CamundaExporterIT.this.shouldExportRecordOnceBulkSizeReached();
    }

    @Test
    void shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater() {
      CamundaExporterIT.this
          .shouldExportRecordIfElasticsearchIsNotInitiallyReachableButThenIsReachableLater(
              CONTAINER,
              () ->
                  client
                      .search(
                          s -> s.index("camunda-record-identity-users-8.7.0_"), UserEntity.class)
                      .hits()
                      .total()
                      .value());
    }

    @Test
    void shouldUpdateExporterPositionAfterFlushing() {
      CamundaExporterIT.this.shouldUpdateExporterPositionAfterFlushing();
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
              .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

      CONFIG.getConnect().setType("mysql");

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @Test
    void shouldNotAllowUnderscoreInIndexPrefix() {
      // given
      final var exporter = new CamundaExporter();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

      CONFIG.getIndex().setPrefix("i_am_invalid");

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
              .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

      CONFIG.getIndex().setNumberOfShards(invalidNumberOfShards);

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
              .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));
      CONFIG.getRetention().setMinimumAge(invalidMinAge);

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
              .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));
      CONFIG.getIndex().setNumberOfReplicas(-1);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }
  }
}
