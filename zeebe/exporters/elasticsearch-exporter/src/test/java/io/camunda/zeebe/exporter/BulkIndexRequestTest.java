/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.BulkIndexRequest.IndexOperation;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Execution(ExecutionMode.CONCURRENT)
final class BulkIndexRequestTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private static final int PARTITION_ID = 1;

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  private final ProtocolFactory recordFactory = new ProtocolFactory();
  private final BulkIndexRequest request = new BulkIndexRequest();

  @Test
  void shouldReturnMemoryUsageAsLengthOfAllSerializedRecords() throws IOException {
    // given
    final var records =
        recordFactory
            .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
            .limit(2)
            .toList();
    final var actions =
        List.of(
            new BulkIndexAction("index", "id", "routing"),
            new BulkIndexAction("index2", "id2", "routing2"));

    final var recordSequence1 = new RecordSequence(PARTITION_ID, 1);
    final var recordSequence2 = new RecordSequence(PARTITION_ID, 2);

    // when
    request.index(actions.get(0), records.get(0), recordSequence1);
    request.index(actions.get(1), records.get(1), recordSequence2);

    // then
    final var expectedMemoryUsage =
        getRecordMemoryUsage(records.get(0), recordSequence1)
            + getRecordMemoryUsage(records.get(1), recordSequence2);
    assertThat(request.memoryUsageBytes()).isEqualTo(expectedMemoryUsage);
  }

  private static int getRecordMemoryUsage(
      final Record<RecordValue> record, final RecordSequence recordSequence) throws IOException {

    final var serializedRecord = MAPPER.writeValueAsBytes(record);
    final var recordAsMap = MAPPER.readValue(serializedRecord, MAP_TYPE_REFERENCE);
    // The sequence property is not part of the record itself. It is added additionally in the
    // Elasticsearch exporter. We need to do the same in the test to get the correct memory usage.
    recordAsMap.put("sequence", recordSequence.sequence());
    recordAsMap.remove("authorizations");
    recordAsMap.remove("agent");
    return MAPPER.writeValueAsBytes(recordAsMap).length;
  }

  @Test
  void shouldClear() {
    // given
    final var records =
        recordFactory
            .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
            .limit(2)
            .toList();
    final var actions =
        List.of(
            new BulkIndexAction("index", "id", "routing"),
            new BulkIndexAction("index2", "id2", "routing2"));
    request.index(actions.get(0), records.get(0), new RecordSequence(PARTITION_ID, 1));
    request.index(actions.get(1), records.get(1), new RecordSequence(PARTITION_ID, 2));

    // when
    request.clear();

    // then
    assertThat(request.bulkOperations()).isEmpty();
    assertThat(request.isEmpty()).isTrue();
    assertThat(request.memoryUsageBytes()).isEqualTo(0);
    assertThat(request.size()).isEqualTo(0);
    assertThat(request.lastIndexedMetadata()).isNull();
  }

  @Nested
  final class IndexTest {
    @Test
    void shouldNotIndexWithIdenticalMetadata() {
      // given
      final var records =
          recordFactory
              .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
              .limit(2)
              .toList();
      final var action = new BulkIndexAction("index", "id", "routing");

      // when - doesn't matter what the records are, if the metadata is the same we skip it
      request.index(action, records.get(0), new RecordSequence(PARTITION_ID, 1));
      request.index(action, records.get(1), new RecordSequence(PARTITION_ID, 1));

      // then
      assertThat(request.bulkOperations())
          .extracting(IndexOperation::metadata)
          .containsExactly(action);
      assertThat(request.lastIndexedMetadata()).isEqualTo(action);
      assertThat(request.isEmpty()).isFalse();
    }

    @Test
    void shouldIndexWithDifferentMetadata() {
      // given
      final var records =
          recordFactory
              .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
              .limit(2)
              .toList();
      final var actions =
          List.of(
              new BulkIndexAction("index", "id", "routing"),
              new BulkIndexAction("index2", "id2", "routing2"));

      // when
      request.index(actions.get(0), records.get(0), new RecordSequence(PARTITION_ID, 1));
      request.index(actions.get(1), records.get(1), new RecordSequence(PARTITION_ID, 2));

      // then
      assertThat(request.bulkOperations())
          .extracting(IndexOperation::metadata)
          .containsExactlyElementsOf(actions);
      assertThat(request.lastIndexedMetadata()).isEqualTo(actions.get(1));
      assertThat(request.isEmpty()).isFalse();
    }
  }

  @Nested
  final class SerializationTest {
    @Test
    void shouldIndexRecordSerialized() {
      // given
      final var record =
          recordFactory.generateRecord(
              b -> b.withAuthorizations(Map.of()).withBrokerVersion(VersionUtil.getVersion()));
      final var action = new BulkIndexAction("index", "id", "routing");

      // when
      request.index(action, record, new RecordSequence(PARTITION_ID, 1));

      // then - verify the record was serialized with expected properties
      final var operations = request.bulkOperations();
      assertThat(operations).hasSize(1);

      final var deserialized = deserializeSource(operations.getFirst());
      // Verify key record properties are serialized correctly
      assertThat(deserialized.getPartitionId()).isEqualTo(record.getPartitionId());
      assertThat(deserialized.getPosition()).isEqualTo(record.getPosition());
      assertThat(deserialized.getKey()).isEqualTo(record.getKey());
      // Verify excluded fields are null/empty
      assertThat(deserialized.getAuthorizations()).isEmpty();
      assertThat(deserialized.getAgent()).isNull();
    }

    @Test
    void shouldIndexRecordWithSequence() {
      // given
      final var records =
          recordFactory
              .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
              .limit(2)
              .toList();

      final var actions =
          List.of(
              new BulkIndexAction("index", "id", "routing"),
              new BulkIndexAction("index2", "id2", "routing2"));

      final var recordSequences =
          List.of(new RecordSequence(PARTITION_ID, 10), new RecordSequence(PARTITION_ID, 20));

      // when
      request.index(actions.get(0), records.get(0), recordSequences.get(0));
      request.index(actions.get(1), records.get(1), recordSequences.get(1));

      // then
      assertThat(request.bulkOperations())
          .hasSize(2)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("sequence"))
          .describedAs("Expect that the records are serialized with the sequences")
          .containsExactly(recordSequences.get(0).sequence(), recordSequences.get(1).sequence());
    }

    @Test
    void shouldIndexRecordWithoutAuthorizationsAndAgent() {
      // given
      final var records =
          recordFactory
              .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
              .limit(1)
              .toList();

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), records.getFirst(), new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("authorizations"))
          .describedAs("Expect that the records are NOT serialized with authorizations")
          .containsExactly(new Object[] {null});
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("agent"))
          .describedAs("Expect that the records are NOT serialized with agent")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexCommandDistributionRecordWithoutAuthInfoOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new CommandDistributionRecord()
                              .setPartitionId(1)
                              .setAuthInfo(new AuthInfo().setAuthData("test-data"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("authInfo"))
          .describedAs("Expect that the records are NOT serialized with authInfo")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexCommandDistributionRecordWithoutAuthInfo() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new CommandDistributionRecord()
                              .setPartitionId(1)
                              .setAuthInfo(new AuthInfo().setAuthData("test-data"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("authInfo"))
          .describedAs("Expect that the records are NOT serialized with authInfo")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexCheckpointRecordWithoutTypeOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new CheckpointRecord()
                              .setCheckpointType(CheckpointType.SCHEDULED_BACKUP)
                              .setCheckpointId(100)
                              .setCheckpointPosition(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("checkpointType"))
          .describedAs("Expect that the records are serialized without type")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexCheckpointRecordWithType() {
      // given
      final var timestamp = Instant.now();
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new CheckpointRecord()
                              .setCheckpointType(CheckpointType.SCHEDULED_BACKUP)
                              .setCheckpointId(100)
                              .setCheckpointPosition(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("checkpointType"))
          .describedAs("Expect that the records are serialized with type")
          .containsExactly(CheckpointType.SCHEDULED_BACKUP.name());
    }

    @Test
    void shouldIndexMessageSubscriptionRecordWithoutProcessDefinitionKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new MessageSubscriptionRecord()
                              .setProcessInstanceKey(1L)
                              .setElementInstanceKey(2L)
                              .setProcessDefinitionKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized without processDefinitionKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexMessageSubscriptionRecordWithProcessDefinitionKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new MessageSubscriptionRecord()
                              .setProcessInstanceKey(1L)
                              .setElementInstanceKey(2L)
                              .setProcessDefinitionKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized with processDefinitionKey")
          .containsExactly(12345);
    }

    @Test
    void shouldIndexProcessMessageSubscriptionRecordWithoutProcessDefinitionKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessMessageSubscriptionRecord()
                              .setProcessInstanceKey(1L)
                              .setElementInstanceKey(2L)
                              .setProcessDefinitionKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized without processDefinitionKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessMessageSubscriptionRecordWithProcessDefinitionKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessMessageSubscriptionRecord()
                              .setProcessInstanceKey(1L)
                              .setElementInstanceKey(2L)
                              .setProcessDefinitionKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized with processDefinitionKey")
          .containsExactly(12345);
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithoutMoveInstructionsOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .addMoveInstruction(
                                  new ProcessInstanceModificationMoveInstruction()
                                      .setSourceElementId("foo")
                                      .setTargetElementId("bar"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("moveInstructions"))
          .describedAs("Expect that the records are serialized without moveInstructions")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithoutIdInTerminateOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .addTerminateInstruction(
                                  new ProcessInstanceModificationTerminateInstruction()
                                      .setElementId("foo")
                                      .setElementInstanceKey(5L))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("terminateInstructions"))
          .extracting(
              source -> {
                final var values = (Map<String, Object>) (((List<Object>) source).getFirst());
                return tuple(values.get("elementId"), values.get("elementInstanceKey"));
              })
          .describedAs(
              "Expect that the records are serialized without elementId in terminateInstructions")
          .containsExactly(tuple(null, 5));
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithMoveInstructions() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .addMoveInstruction(
                                  new ProcessInstanceModificationMoveInstruction()
                                      .setSourceElementId("foo")
                                      .setTargetElementId("bar"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("moveInstructions"))
          .extracting(source -> ((List<Object>) source).getFirst())
          .extracting("sourceElementId", "targetElementId")
          .describedAs("Expect that the records are serialized with moveInstructions")
          .containsExactly(tuple("foo", "bar"));
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithIdInTerminate() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .addTerminateInstruction(
                                  new ProcessInstanceModificationTerminateInstruction()
                                      .setElementId("foo")
                                      .setElementInstanceKey(5L))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("terminateInstructions"))
          .extracting(source -> ((List<Object>) source).getFirst())
          .extracting("elementId", "elementInstanceKey")
          .describedAs(
              "Expect that the records are serialized with elementId in terminateInstructions")
          .containsExactly(tuple("foo", 5));
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithoutBusinessIdOnPreviousVersion(final ValueType valueType) throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      final String businessId = getBusinessId(record.getValue());
      assertThat(businessId).isNotEmpty();

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("businessId"))
          .describedAs("Expect that the records are serialized without businessId")
          .containsExactly(new Object[] {null});
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithBusinessIdOnCurrentVersion(final ValueType valueType) throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      final String businessId = getBusinessId(record.getValue());
      assertThat(businessId).isNotEmpty();

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("businessId"))
          .describedAs("Expect that the records are serialized with businessId")
          .containsExactly(businessId);
    }

    private static String getBusinessId(final RecordValue recordValue) throws Exception {
      final var field = recordValue.getClass().getDeclaredField("businessId");
      field.setAccessible(true);
      return (String) field.get(recordValue);
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "DECISION_EVALUATION",
          "INCIDENT",
          "JOB",
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
          "PROCESS_INSTANCE_MIGRATION",
          "PROCESS_INSTANCE_MODIFICATION",
          "PROCESS_MESSAGE_SUBSCRIPTION",
          "USER_TASK",
          "VARIABLE",
        })
    void shouldIndexWithoutRootProcessInstanceKeyOnPreviousVersion(final ValueType valueType)
        throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      final long rootProcessInstanceKey = getRootProcessInstanceKey(record.getValue());
      assertThat(rootProcessInstanceKey).isPositive();

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("rootProcessInstanceKey"))
          .describedAs("Expect that the records are serialized without rootProcessInstanceKey")
          .containsExactly(new Object[] {null});
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "DECISION_EVALUATION",
          "INCIDENT",
          "JOB",
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
          "PROCESS_INSTANCE_MIGRATION",
          "PROCESS_INSTANCE_MODIFICATION",
          "PROCESS_MESSAGE_SUBSCRIPTION",
          "USER_TASK",
          "VARIABLE",
        })
    void shouldIndexWithRootProcessInstanceKey(final ValueType valueType) throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final long rootProcessInstanceKey = getRootProcessInstanceKey(record.getValue());
      assertThat(rootProcessInstanceKey).isPositive();

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("rootProcessInstanceKey"))
          .describedAs("Expect that the records are serialized with rootProcessInstanceKey")
          .containsExactly(rootProcessInstanceKey);
    }

    @Test
    void shouldIndexJobRecordWithoutJobToUserTaskMigrationOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new JobRecord().setType("test-job").setIsJobToUserTaskMigration(true)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("jobToUserTaskMigration"))
          .describedAs(
              "Expect that job records are serialized without jobToUserTaskMigration on previous version")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexJobRecordWithJobToUserTaskMigration() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new JobRecord().setType("test-job").setIsJobToUserTaskMigration(true)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("jobToUserTaskMigration"))
          .describedAs(
              "Expect that job records are serialized with jobToUserTaskMigration on current version")
          .containsExactly(true);
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithoutElementInstanceKeyOnPreviousVersion(final ValueType valueType) {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("elementInstanceKey"))
          .describedAs("Expect that the records are serialized without elementInstanceKey")
          .containsExactly(new Object[] {null});
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithElementInstanceKeyOnCurrentVersion(final ValueType valueType) {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("elementInstanceKey"))
          .describedAs("Expect that the records are serialized with elementInstanceKey")
          .doesNotContainNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithoutTagsOnPreviousVersion(final ValueType valueType) {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tags"))
          .describedAs("Expect that the records are serialized without tags")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexJobBatchRecordWithoutTenantFilterOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new JobBatchRecord()
                              .setType("test")
                              .setTenantFilter(TenantFilter.ASSIGNED)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tenantFilter"))
          .describedAs("Expect that the records are serialized without tenantFilter")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexJobBatchRecordWithTenantFilter() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new JobBatchRecord()
                              .setType("test")
                              .setTenantFilter(TenantFilter.ASSIGNED)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tenantFilter"))
          .describedAs("Expect that the records are serialized with tenantFilter")
          .containsExactly(TenantFilter.ASSIGNED.name());
    }

    @Test
    void shouldIndexVariableRecordWithoutSourceOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.VARIABLE, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("source"))
          .describedAs("Expect that the records are serialized without source")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexVariableRecordWithoutElementInstanceKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.VARIABLE, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("elementInstanceKey"))
          .describedAs("Expect that the records are serialized without elementInstanceKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexUserTaskRecordWithoutListenersConfigKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new UserTaskRecord()
                              .setUserTaskKey(1L)
                              .setListenersConfigKey(42L)
                              .setTags(Set.of("tag1"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("listenersConfigKey"))
          .describedAs("Expect that the records are serialized without listenersConfigKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexUserTaskRecordWithoutTagsOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new UserTaskRecord().setUserTaskKey(1L).setTags(Set.of("tag1", "tag2"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tags"))
          .describedAs("Expect that the records are serialized without tags")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexUserTaskRecordWithTagsOnCurrentVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new UserTaskRecord().setUserTaskKey(1L).setTags(Set.of("tag1"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tags"))
          .describedAs("Expect that the records are serialized with tags")
          .doesNotContainNull();
    }

    @Test
    void shouldIndexProcessInstanceMigrationWithoutNewFieldsOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.PROCESS_INSTANCE_MIGRATION,
              r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .allSatisfy(
              value -> {
                final var valueMap = (Map<String, Object>) value;
                assertThat(valueMap.get("processDefinitionKey"))
                    .describedAs("processDefinitionKey should be absent")
                    .isNull();
                assertThat(valueMap.get("tenantId"))
                    .describedAs("tenantId should be absent")
                    .isNull();
                assertThat(valueMap.get("bpmnProcessId"))
                    .describedAs("bpmnProcessId should be absent")
                    .isNull();
                assertThat(valueMap.get("elementInstanceKey"))
                    .describedAs("elementInstanceKey should be absent")
                    .isNull();
              });
    }

    @Test
    void shouldIndexDecisionRequirementsRecordWithoutDeploymentKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(new DecisionRequirementsRecord().setDeploymentKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized without deploymentKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexDecisionRequirementsRecordWithDeploymentKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new DecisionRequirementsRecord().setDeploymentKey(12345L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized with deploymentKey")
          .containsExactly(12345);
    }

    @Test
    void shouldIndexProcessInstanceBatchRecordWithoutProcessDefinitionKeyOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.PROCESS_INSTANCE_BATCH,
              r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized without processDefinitionKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessInstanceBatchRecordWithProcessDefinitionKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.PROCESS_INSTANCE_BATCH, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized with processDefinitionKey")
          .doesNotContainNull();
    }

    @Test
    void shouldIndexResourceDeletionRecordWithoutNewFieldsOnPreviousVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.RESOURCE_DELETION,
              r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .allSatisfy(
              value -> {
                final var valueMap = (Map<String, Object>) value;
                assertThat(valueMap.get("deleteHistory"))
                    .describedAs("deleteHistory should be absent")
                    .isNull();
                assertThat(valueMap.get("batchOperationKey"))
                    .describedAs("batchOperationKey should be absent")
                    .isNull();
                assertThat(valueMap.get("batchOperationType"))
                    .describedAs("batchOperationType should be absent")
                    .isNull();
                assertThat(valueMap.get("resourceType"))
                    .describedAs("resourceType should be absent")
                    .isNull();
                assertThat(valueMap.get("resourceId"))
                    .describedAs("resourceId should be absent")
                    .isNull();
              });
    }

    @Test
    void shouldIndexResourceDeletionRecordWithNewFieldsOnCurrentVersion() {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.RESOURCE_DELETION, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .allSatisfy(
              value -> {
                final var valueMap = (Map<String, Object>) value;
                assertThat(valueMap.get("deleteHistory"))
                    .describedAs("deleteHistory should be present")
                    .isNotNull();
                assertThat(valueMap.get("batchOperationKey"))
                    .describedAs("batchOperationKey should be present")
                    .isNotNull();
                assertThat(valueMap.get("batchOperationType"))
                    .describedAs("batchOperationType should be present")
                    .isNotNull();
                assertThat(valueMap.get("resourceType"))
                    .describedAs("resourceType should be present")
                    .isNotNull();
                assertThat(valueMap.get("resourceId"))
                    .describedAs("resourceId should be present")
                    .isNotNull();
              });
    }

    private static long getRootProcessInstanceKey(final RecordValue recordValue) throws Exception {
      final var field = recordValue.getClass().getDeclaredField("rootProcessInstanceKey");
      field.setAccessible(true);
      return (long) field.get(recordValue);
    }

    private Record<?> deserializeSource(final IndexOperation operation) {
      try {
        return MAPPER.readValue(operation.source(), new TypeReference<>() {});
      } catch (final IOException e) {
        throw new UncheckedIOException(
            String.format("Failed to deserialize operation [%s] source", operation.metadata()), e);
      }
    }
  }
}
