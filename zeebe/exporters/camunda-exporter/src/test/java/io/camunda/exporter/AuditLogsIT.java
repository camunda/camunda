/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.ImmutableHistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableForm;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AuditLogsIT {
  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private String testPrefix;

  @BeforeEach
  public void beforeEach() {
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
  }

  @AfterEach
  public void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(testPrefix + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(testPrefix + "*"));
  }

  @TestTemplate
  void shouldWriteAuditLogAndCleanup(
      final ExporterConfiguration config, final SearchClientAdapter client) throws IOException {
    // given
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfReplicas(0);
    config.getAuditLog().setEnabled(true);
    config.getAuditLog().getUnknown().setCategories(Set.of(AuditLogOperationCategory.values()));
    // force flush after every record to ensure that the audit log is written to the index
    // immediately
    config.getBulk().setSize(1);

    createSchemas(config);

    final var resourceProvider = new DefaultExporterResourceProvider();

    final var exporter = new CamundaExporter(new TargetIndexLocator(), resourceProvider);
    try {
      final var context =
          new ExporterTestContext()
              .setConfiguration(
                  new ExporterTestConfiguration<>(config.getConnect().getType(), config))
              .setPartitionId(1);
      exporter.configure(context);

      final var exporterController = new ExporterTestController();
      exporter.open(exporterController);

      final var testParameters = provideTestParameters();

      // when
      for (final var testParameter : testParameters) {
        exporter.export(testParameter.record());
      }

      // then
      client.refresh(testPrefix);

      final var auditLogTemplate =
          resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);
      final var auditLogCleanupIndex =
          resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);

      final var auditLogEntries = searchAllById(client, auditLogTemplate.getFullQualifiedName());
      final var cleanupEntries = searchAllById(client, auditLogCleanupIndex.getFullQualifiedName());

      for (final var testParameter : testParameters) {
        final var expectedAuditLog = testParameter.expectedAuditLog();
        final var expectedCleanup = testParameter.expectedCleanup();
        if (expectedAuditLog != null) {
          final var id =
              Objects.requireNonNull(
                      expectedAuditLog.get("id"),
                      "Expected audit log entry must have an 'id' field: " + expectedAuditLog)
                  .toString();
          assertThat(auditLogEntries)
              .describedAs(testParameter.record() + " should create audit log entry")
              .containsEntry(id, expectedAuditLog);
          auditLogEntries.remove(id);
        }
        if (expectedCleanup != null) {
          final var id =
              Objects.requireNonNull(
                      expectedCleanup.get("id"),
                      "Expected cleanup entry must have an 'id' field: " + expectedCleanup)
                  .toString();
          assertThat(cleanupEntries)
              .describedAs(testParameter.record() + " should create cleanup entry")
              .containsEntry(id, expectedCleanup);
          cleanupEntries.remove(id);
        }
      }

      assertThat(auditLogEntries)
          .describedAs("Unexpected audit log entries found: " + auditLogEntries.values())
          .isEmpty();
      assertThat(cleanupEntries)
          .describedAs("Unexpected cleanup entries found: " + cleanupEntries.values())
          .isEmpty();
    } finally {
      exporter.close();
    }
  }

  private Map<String, Map<String, Object>> searchAllById(
      final SearchClientAdapter client, final String indexName) throws IOException {
    return client.searchAll(indexName, Map.class).stream()
        .map(this::removeNullValues)
        .collect(Collectors.toMap(doc -> doc.get("id").toString(), doc -> doc));
  }

  private Map<String, Object> removeNullValues(final Map<String, Object> map) {
    return map.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<TestParameter> provideTestParameters() {
    // this is not a complete list - just trying to get enough to cover the main audit log and
    // cleanup scenarios for now
    return List.of(
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.USER)
                    .withIntent(UserIntent.CREATED)
                    .withValue(ImmutableUserRecordValue.builder().withUsername("user-1").build())
                    .withKey(1234L)
                    .withPosition(789L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T13:00:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-789"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "ADMIN"),
                    Map.entry("entityKey", "user-1"),
                    Map.entry("entityOperationIntent", (int) UserIntent.CREATED.value()),
                    Map.entry("entityType", "USER"),
                    Map.entry("entityValueType", (int) ValueType.USER.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("operationType", "CREATE"),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "GLOBAL"),
                    Map.entry("timestamp", "2026-07-15T13:00:00.000+0000")))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.USER)
                    .withIntent(UserIntent.DELETED)
                    .withValue(ImmutableUserRecordValue.builder().withUsername("user-1").build())
                    .withKey(1234L)
                    .withPosition(800L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T13:05:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-800"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "ADMIN"),
                    Map.entry("entityKey", "user-1"),
                    Map.entry("entityOperationIntent", (int) UserIntent.DELETED.value()),
                    Map.entry("entityType", "USER"),
                    Map.entry("entityValueType", (int) ValueType.USER.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("operationType", "DELETE"),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "GLOBAL"),
                    Map.entry("timestamp", "2026-07-15T13:05:00.000+0000")))
            .expectedCleanup(
                Map.ofEntries(
                    Map.entry("id", "1-800"),
                    Map.entry("entityType", "USER"),
                    Map.entry("key", "user-1"),
                    Map.entry("keyField", "entityKey"),
                    Map.entry("partitionId", 1)))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.PROCESS)
                    .withIntent(ProcessIntent.CREATED)
                    .withValue(
                        ImmutableProcess.builder()
                            .withProcessDefinitionKey(2345L)
                            .withBpmnProcessId("my-process")
                            .withResource("<processdef />".getBytes(StandardCharsets.UTF_8))
                            .build())
                    .withKey(2345L)
                    .withPosition(801L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T14:00:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-801"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "DEPLOYED_RESOURCES"),
                    Map.entry("entityKey", "2345"),
                    Map.entry("entityOperationIntent", (int) ProcessIntent.CREATED.value()),
                    Map.entry("entityType", "RESOURCE"),
                    Map.entry("entityValueType", (int) ValueType.PROCESS.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("operationType", "CREATE"),
                    Map.entry("processDefinitionId", "my-process"),
                    Map.entry("processDefinitionKey", 2345),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "TENANT"),
                    Map.entry("timestamp", "2026-07-15T14:00:00.000+0000")))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.PROCESS)
                    .withIntent(ProcessIntent.DELETED)
                    .withValue(
                        ImmutableProcess.builder()
                            .withProcessDefinitionKey(2345L)
                            .withBpmnProcessId("my-process")
                            .build())
                    .withKey(2345L)
                    .withPosition(802L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T14:05:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-802"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "DEPLOYED_RESOURCES"),
                    Map.entry("entityKey", "2345"),
                    Map.entry("entityOperationIntent", (int) ProcessIntent.DELETED.value()),
                    Map.entry("entityType", "RESOURCE"),
                    Map.entry("entityValueType", (int) ValueType.PROCESS.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("operationType", "DELETE"),
                    Map.entry("processDefinitionId", "my-process"),
                    Map.entry("processDefinitionKey", 2345),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "TENANT"),
                    Map.entry("timestamp", "2026-07-15T14:05:00.000+0000")))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.HISTORY_DELETION)
                    .withIntent(HistoryDeletionIntent.DELETED)
                    .withValue(
                        ImmutableHistoryDeletionRecordValue.builder()
                            .withResourceType(HistoryDeletionType.PROCESS_INSTANCE)
                            .withResourceKey(3456L)
                            .build())
                    .withKey(4567L)
                    .withPosition(803L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T15:00:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-803"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "DEPLOYED_RESOURCES"),
                    Map.entry("entityDescription", "PROCESS_INSTANCE"),
                    Map.entry("entityKey", "3456"),
                    Map.entry("entityOperationIntent", (int) HistoryDeletionIntent.DELETED.value()),
                    Map.entry("entityType", "PROCESS_INSTANCE"),
                    Map.entry("entityValueType", (int) ValueType.HISTORY_DELETION.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("operationType", "DELETE"),
                    Map.entry("processInstanceKey", 3456),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "TENANT"),
                    Map.entry("timestamp", "2026-07-15T15:00:00.000+0000")))
            .expectedCleanup(
                Map.ofEntries(
                    Map.entry("id", "1-803"),
                    Map.entry("entityType", "PROCESS_INSTANCE"),
                    Map.entry("key", "3456"),
                    Map.entry("keyField", "entityKey"),
                    Map.entry("partitionId", 1)))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.FORM)
                    .withIntent(FormIntent.CREATED)
                    .withValue(
                        ImmutableForm.builder()
                            .withFormKey(8888L)
                            .withResource("<form />".getBytes(StandardCharsets.UTF_8))
                            .build())
                    .withKey(8888L)
                    .withPosition(804L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T16:00:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-804"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "DEPLOYED_RESOURCES"),
                    Map.entry("entityKey", "8888"),
                    Map.entry("entityOperationIntent", (int) FormIntent.CREATED.value()),
                    Map.entry("entityType", "RESOURCE"),
                    Map.entry("entityValueType", (int) ValueType.FORM.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("formKey", 8888),
                    Map.entry("operationType", "CREATE"),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "TENANT"),
                    Map.entry("timestamp", "2026-07-15T16:00:00.000+0000")))
            .build(),
        TestParameter.record(
                ImmutableRecord.builder()
                    .withRecordType(RecordType.EVENT)
                    .withValueType(ValueType.FORM)
                    .withIntent(FormIntent.DELETED)
                    .withValue(
                        ImmutableForm.builder()
                            .withFormKey(8888L)
                            .withResource("<form />".getBytes(StandardCharsets.UTF_8))
                            .build())
                    .withKey(8888L)
                    .withPosition(805L)
                    .withPartitionId(1)
                    .withTimestamp(Instant.parse("2026-07-15T16:05:00Z").toEpochMilli())
                    .build())
            .expectedAuditLog(
                Map.ofEntries(
                    Map.entry("id", "1-805"),
                    Map.entry("actorType", "UNKNOWN"),
                    Map.entry("category", "DEPLOYED_RESOURCES"),
                    Map.entry("entityKey", "8888"),
                    Map.entry("entityOperationIntent", (int) FormIntent.DELETED.value()),
                    Map.entry("entityType", "RESOURCE"),
                    Map.entry("entityValueType", (int) ValueType.FORM.value()),
                    Map.entry("entityVersion", 0),
                    Map.entry("formKey", 8888),
                    Map.entry("operationType", "DELETE"),
                    Map.entry("result", "SUCCESS"),
                    Map.entry("tenantScope", "TENANT"),
                    Map.entry("timestamp", "2026-07-15T16:05:00.000+0000")))
            .expectedCleanup(
                Map.ofEntries(
                    Map.entry("id", "1-805"),
                    Map.entry("entityType", "RESOURCE"),
                    Map.entry("key", "8888"),
                    Map.entry("keyField", "entityKey"),
                    Map.entry("partitionId", 1)))
            .build());
  }

  record TestParameter(
      Record<?> record, Map<String, Object> expectedAuditLog, Map<String, Object> expectedCleanup) {
    static TestParameterBuilder record(final Record<?> record) {
      return new TestParameterBuilder(record);
    }
  }

  static class TestParameterBuilder {
    private final Record<?> record;
    private Map<String, Object> expectedAuditLog;
    private Map<String, Object> expectedCleanup;

    public TestParameterBuilder(final Record<?> record) {
      this.record = record;
    }

    public TestParameterBuilder expectedAuditLog(final Map<String, Object> expectedAuditLog) {
      this.expectedAuditLog = expectedAuditLog;
      return this;
    }

    public TestParameterBuilder expectedCleanup(final Map<String, Object> expectedCleanup) {
      this.expectedCleanup = expectedCleanup;
      return this;
    }

    public TestParameter build() {
      return new TestParameter(record, expectedAuditLog, expectedCleanup);
    }
  }
}
