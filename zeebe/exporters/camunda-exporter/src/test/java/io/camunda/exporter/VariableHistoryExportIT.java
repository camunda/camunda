/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration tests of the variable history handler, which writes one document per recorded
 * variable change, next to the current value that the variable handler keeps.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class VariableHistoryExportIT {

  private static final int PARTITION_ID = 1;
  private static final long PROCESS_DEFINITION_KEY = 1001L;
  private static final long PROCESS_INSTANCE_KEY = 2001L;
  private static final long TIMESTAMP = 1_700_000_000_123L;
  private static final Map<String, Object> USER = Map.of(Authorization.AUTHORIZED_USERNAME, "demo");
  private static final Map<String, Object> CLIENT =
      Map.of(Authorization.AUTHORIZED_CLIENT_ID, "billing-client");

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private final ProtocolFactory factory = new ProtocolFactory();
  private CamundaExporter exporter;
  private ExporterTestController controller;

  @AfterEach
  void afterEach() throws IOException {
    if (exporter != null) {
      exporter.close();
    }
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldWriteADocumentForEachOfTwoChangesInTheSameBatch(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    config.getAuditLog().setEnabled(true);
    config.getAuditLog().setVariableHistoryEnabled(true);
    // the template extension flushes after every record, so raise the size to keep both in a batch
    // and flush it by time, with a clock that the test moves
    config.getBulk().setSize(100);
    final var now = new AtomicLong(Instant.now().toEpochMilli());
    exporter = createAndOpenExporter(config, () -> Instant.ofEpochMilli(now.get()));
    final var created =
        variable(
            VariableIntent.CREATED, "approved", "false", VariableOperationType.API, USER, 100L);
    final var updated =
        variable(VariableIntent.UPDATED, "approved", "true", VariableOperationType.API, USER, 101L);

    // when both changes are exported, and the batch is flushed once its delay has passed
    exporter.export(created);
    exporter.export(updated);
    clientAdapter.refresh();
    final var writtenBeforeTheFlush =
        clientAdapter.searchAll(historyIndexPattern(config), VariableHistoryEntity.class);
    now.addAndGet(Duration.ofSeconds(config.getBulk().getDelay() + 1).toMillis());
    controller.runScheduledTasks(Duration.ofSeconds(config.getBulk().getDelay() + 1));
    clientAdapter.refresh();

    // then both were waiting in the batch, and one flush wrote both
    assertThat(writtenBeforeTheFlush).isEmpty();
    final var documents =
        clientAdapter.searchAll(historyIndexPattern(config), VariableHistoryEntity.class).stream()
            .sorted(Comparator.comparing(VariableHistoryEntity::getVariableHistoryItemKey))
            .toList();
    assertThat(documents).hasSize(2);

    final var first = documents.get(0);
    assertThat(first.getId()).isEqualTo(PARTITION_ID + "-100");
    assertThat(first.getVariableHistoryItemKey())
        .isEqualTo(Protocol.encodePartitionId(PARTITION_ID, 100L));
    assertThat(first.getTimestamp().toInstant()).isEqualTo(Instant.ofEpochMilli(TIMESTAMP));
    assertThat(first.getName()).isEqualTo("approved");
    assertThat(first.getValue()).isEqualTo("false");
    assertThat(first.getOperationType()).isEqualTo(VariableHistoryOperationType.CREATE);
    assertThat(first.getVariableOperationType()).isEqualTo(VariableOperationType.API);
    assertThat(first.getActorType()).isEqualTo(VariableHistoryActorType.USER);
    assertThat(first.getActorId()).isEqualTo("demo");
    assertThat(first.getElementInstanceKey()).isNull();
    assertThat(first.getElementId()).isNull();

    final var second = documents.get(1);
    assertThat(second.getId()).isEqualTo(PARTITION_ID + "-101");
    assertThat(second.getVariableHistoryItemKey()).isGreaterThan(first.getVariableHistoryItemKey());
    assertThat(second.getValue()).isEqualTo("true");
    assertThat(second.getOperationType()).isEqualTo(VariableHistoryOperationType.UPDATE);
  }

  @TestTemplate
  void shouldWriteTheChangeOfAUserAndNoneOfAClientOrTheEngineWithTheDefaultSections(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    config.getAuditLog().setEnabled(true);
    config.getAuditLog().setVariableHistoryEnabled(true);
    exporter = createAndOpenExporter(config);

    // when a user, a client and an output mapping under the command of a user change a variable
    exporter.export(
        variable(VariableIntent.CREATED, "byUser", "1", VariableOperationType.API, USER, 100L));
    exporter.export(
        variable(VariableIntent.CREATED, "byClient", "2", VariableOperationType.API, CLIENT, 101L));
    exporter.export(
        variable(
            VariableIntent.CREATED,
            "byEngine",
            "3",
            VariableOperationType.OUTPUT_MAPPING,
            USER,
            102L));
    controller.runScheduledTasks(Duration.ofSeconds(1));
    clientAdapter.refresh();

    // then only the change of the user has a document
    final var documents =
        clientAdapter.searchAll(historyIndexPattern(config), VariableHistoryEntity.class);
    assertThat(documents).extracting(VariableHistoryEntity::getName).containsExactly("byUser");
  }

  @TestTemplate
  void shouldWriteAValueAboveTheThresholdAsPreviewAndFullValue(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given a value far above the threshold, and above the size that a keyword term accepts
    createSchemas(config);
    config.getAuditLog().setEnabled(true);
    config.getAuditLog().setVariableHistoryEnabled(true);
    exporter = createAndOpenExporter(config);
    final var threshold = config.getIndex().getVariableSizeThreshold();
    final var longValue = "\"" + "x".repeat(40_000) + "\"";

    // when
    exporter.export(
        variable(
            VariableIntent.CREATED, "report", longValue, VariableOperationType.API, USER, 100L));
    controller.runScheduledTasks(Duration.ofSeconds(1));
    clientAdapter.refresh();

    // then
    final var documents =
        clientAdapter.searchAll(historyIndexPattern(config), VariableHistoryEntity.class);
    assertThat(documents).hasSize(1);
    final var document = documents.getFirst();
    assertThat(document.getIsPreview()).isTrue();
    assertThat(document.getValue()).isEqualTo(longValue.substring(0, threshold));
    assertThat(document.getFullValue()).isEqualTo(longValue);
  }

  @TestTemplate
  void shouldWriteNothingWhenTheSwitchIsOff(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given the audit log on and the switch off, which is its default
    createSchemas(config);
    config.getAuditLog().setEnabled(true);
    assertThat(config.getAuditLog().isVariableHistoryEnabled()).isFalse();
    exporter = createAndOpenExporter(config);

    // when
    exporter.export(
        variable(
            VariableIntent.CREATED, "approved", "true", VariableOperationType.API, USER, 100L));
    controller.runScheduledTasks(Duration.ofSeconds(1));
    clientAdapter.refresh();

    // then the current value is written, the history is not
    assertThat(
            clientAdapter.get(
                PROCESS_INSTANCE_KEY + "-approved",
                variableIndexName(config),
                VariableEntity.class))
        .isNotNull();
    assertThat(clientAdapter.searchAll(historyIndexPattern(config), VariableHistoryEntity.class))
        .isEmpty();
  }

  private Record<VariableRecordValue> variable(
      final VariableIntent intent,
      final String name,
      final String value,
      final VariableOperationType type,
      final Map<String, Object> authorizations,
      final long position) {
    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withIntent(intent)
                .withBrokerVersion("8.11.0")
                .withPartitionId(PARTITION_ID)
                // a log position that Protocol.encodePartitionId accepts
                .withPosition(position)
                // the random timestamp of the factory is outside the range of a date field
                .withTimestamp(TIMESTAMP)
                .withAuthorizations(authorizations)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .from(factory.generateObject(VariableRecordValue.class))
                        .withName(name)
                        .withValue(value)
                        .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withScopeKey(PROCESS_INSTANCE_KEY)
                        .withBpmnProcessId("variableHistoryProcess")
                        .withSource(new VariableSourceRecord().setType(type))
                        .build()));
  }

  private CamundaExporter createAndOpenExporter(final ExporterConfiguration config) {
    return createAndOpenExporter(config, InstantSource.system());
  }

  private CamundaExporter createAndOpenExporter(
      final ExporterConfiguration config, final InstantSource clock) {
    final var camundaExporter = new CamundaExporter();
    final var context =
        new ExporterTestContext()
            .setClock(clock)
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));
    camundaExporter.configure(context);
    controller = new ExporterTestController();
    camundaExporter.open(controller);
    return camundaExporter;
  }

  private static String historyIndexPattern(final ExporterConfiguration config) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    return new VariableHistoryTemplate(config.getConnect().getIndexPrefix(), isElasticsearch)
        .getIndexPattern();
  }

  private static String variableIndexName(final ExporterConfiguration config) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    return new VariableTemplate(config.getConnect().getIndexPrefix(), isElasticsearch)
        .getFullQualifiedName();
  }
}
