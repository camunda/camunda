/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.RuntimeInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

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
    // Opensearch exporter. We need to do the same in the test to get the correct memory usage.
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
          .extracting(BulkOperation::index)
          .hasSize(1)
          .allSatisfy(
              indexOperation -> {
                assertThat(indexOperation.index()).isEqualTo("index");
                assertThat(indexOperation.id()).isEqualTo("id");
                assertThat(indexOperation.routing()).isEqualTo("routing");
              });
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
          .extracting(BulkOperation::index)
          .hasSize(2)
          .satisfiesExactly(
              indexOp1 -> {
                assertThat(indexOp1.index()).isEqualTo("index");
                assertThat(indexOp1.id()).isEqualTo("id");
                assertThat(indexOp1.routing()).isEqualTo("routing");
              },
              indexOp2 -> {
                assertThat(indexOp2.index()).isEqualTo("index2");
                assertThat(indexOp2.id()).isEqualTo("id2");
                assertThat(indexOp2.routing()).isEqualTo("routing2");
              });
      assertThat(request.lastIndexedMetadata()).isEqualTo(actions.get(1));
      assertThat(request.isEmpty()).isFalse();
    }
  }

  @Nested
  final class SerializationTest {

    @Test
    void shouldIndexRecordSerialized() throws IOException {
      // given - use an empty authorization for comparison, since the bulk request will remove it
      final var record =
          recordFactory.generateRecord(
              b -> b.withAuthorizations(Map.of()).withBrokerVersion(VersionUtil.getVersion()));
      final var action = new BulkIndexAction("index", "id", "routing");

      // when
      request.index(action, record, new RecordSequence(PARTITION_ID, 1));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> doc = getDocumentAsMap(request.bulkOperations().getFirst());

      // Verify key record properties are serialized
      assertThat(doc.get("partitionId")).isEqualTo(record.getPartitionId());
      assertThat(doc.get("position")).isEqualTo(record.getPosition());
      assertThat(doc.get("sequence")).isEqualTo(2251799813685249L);
      assertThat(doc.get("authorizations")).isNull();
      assertThat(doc.get("agent")).isNull();
    }

    @Test
    void shouldIndexRecordWithSequence() throws IOException {
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
      final var operations = request.bulkOperations();
      assertThat(operations).hasSize(2);

      final var doc1 = getDocumentAsMap(operations.get(0));
      assertThat(doc1.get("sequence"))
          .describedAs("Expect that the first record is serialized with sequence 10")
          .isEqualTo(recordSequences.get(0).sequence());

      final var doc2 = getDocumentAsMap(operations.get(1));
      assertThat(doc2.get("sequence"))
          .describedAs("Expect that the second record is serialized with sequence 20")
          .isEqualTo(recordSequences.get(1).sequence());
    }

    @Test
    void shouldIndexRecordWithoutAuthorizationsAndAgent() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final var doc = getDocumentAsMap(request.bulkOperations().getFirst());
      assertThat(doc.get("authorizations"))
          .describedAs("Expect that the records are NOT serialized with authorizations")
          .isNull();
      assertThat(doc.get("agent"))
          .describedAs("Expect that the records are NOT serialized with agent")
          .isNull();
    }

    @Test
    void shouldIndexCommandDistributionRecordWithoutAuthInfoOnPreviousVersion() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("authInfo"))
          .describedAs("Expect that the records are NOT serialized with authInfo")
          .isNull();
    }

    @Test
    void shouldIndexCommandDistributionRecordWithoutAuthInfo() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("authInfo"))
          .describedAs("Expect that the records are NOT serialized with authInfo")
          .isNull();
    }

    @Test
    void shouldIndexCheckpointRecordWithoutTypeAndTimestampOnPreviousVersion() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("checkpointType"))
          .describedAs("Expect that the records are serialized without checkpointType")
          .isNull();
    }

    @Test
    void shouldIndexCheckpointRecordWithTypeAndTimestamp() throws IOException {
      // given
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("checkpointType"))
          .describedAs("Expect that the records are serialized with checkpointType")
          .isEqualTo(CheckpointType.SCHEDULED_BACKUP.name());
    }

    @Test
    void shouldIndexMessageSubscriptionRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized without processDefinitionKey")
          .isNull();
    }

    @Test
    void shouldIndexMessageSubscriptionRecordWithProcessDefinitionKey() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized with processDefinitionKey")
          .isEqualTo(12345);
    }

    @Test
    void shouldIndexProcessMessageSubscriptionRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized without processDefinitionKey")
          .isNull();
    }

    @Test
    void shouldIndexProcessMessageSubscriptionRecordWithProcessDefinitionKey() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs("Expect that the records are serialized with processDefinitionKey")
          .isEqualTo(12345);
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithoutMoveInstructionsOnPreviousVersion()
        throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("moveInstructions"))
          .describedAs("Expect that the records are serialized without moveInstructions")
          .isNull();
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithoutIdInTerminateOnPreviousVersion()
        throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      final List<Map<String, Object>> terminateInstructions =
          (List<Map<String, Object>>) value.get("terminateInstructions");

      assertThat(terminateInstructions).hasSize(1);
      assertThat(terminateInstructions.getFirst().get("elementId"))
          .describedAs("Expect that elementId is NOT serialized in terminateInstructions")
          .isNull();
      assertThat(terminateInstructions.getFirst().get("elementInstanceKey")).isEqualTo(5);
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithMoveInstructions() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      final List<Map<String, Object>> moveInstructions =
          (List<Map<String, Object>>) value.get("moveInstructions");

      assertThat(moveInstructions)
          .describedAs("Expect that the records are serialized with moveInstructions")
          .hasSize(1);
      assertThat(moveInstructions.getFirst().get("sourceElementId")).isEqualTo("foo");
      assertThat(moveInstructions.getFirst().get("targetElementId")).isEqualTo("bar");
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithIdInTerminate() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      final List<Map<String, Object>> terminateInstructions =
          (List<Map<String, Object>>) value.get("terminateInstructions");

      assertThat(terminateInstructions)
          .describedAs("Expect that the records are serialized with terminateInstructions")
          .hasSize(1);
      assertThat(terminateInstructions.getFirst().get("elementId"))
          .describedAs("Expect that elementId IS serialized in terminateInstructions")
          .isEqualTo("foo");
      assertThat(terminateInstructions.getFirst().get("elementInstanceKey")).isEqualTo(5);
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("rootProcessInstanceKey"))
          .describedAs("Expect that the records are serialized without rootProcessInstanceKey")
          .isNull();
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("rootProcessInstanceKey"))
          .describedAs("Expect that the records are serialized with rootProcessInstanceKey")
          .isEqualTo(rootProcessInstanceKey);
    }

    @Test
    void shouldIndexJobRecordWithoutJobToUserTaskMigrationOnPreviousVersion() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("jobToUserTaskMigration"))
          .describedAs(
              "Expect that job records are serialized without jobToUserTaskMigration on previous version")
          .isNull();
    }

    @Test
    void shouldIndexJobRecordWithJobToUserTaskMigration() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("jobToUserTaskMigration"))
          .describedAs(
              "Expect that job records are serialized with jobToUserTaskMigration on current version")
          .isEqualTo(true);
    }

    private static long getRootProcessInstanceKey(final RecordValue recordValue) throws Exception {
      final var field = recordValue.getClass().getDeclaredField("rootProcessInstanceKey");
      field.setAccessible(true);
      return (long) field.get(recordValue);
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("businessId"))
          .describedAs("Expect that the records are serialized without businessId")
          .isNull();
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
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("businessId"))
          .describedAs("Expect that the records are serialized with businessId")
          .isEqualTo(businessId);
    }

    private static String getBusinessId(final RecordValue recordValue) throws Exception {
      final var field = recordValue.getClass().getDeclaredField("businessId");
      field.setAccessible(true);
      return (String) field.get(recordValue);
    }

    private static Map<String, Object> getDocumentAsMap(final BulkOperation operation)
        throws IOException {
      return MAPPER.readValue(
          ((JsonData) operation.index().document()).toJson().toString(), MAP_TYPE_REFERENCE);
    }

    private static Map<String, Object> getValueFromFirstOperation(final BulkIndexRequest request)
        throws IOException {
      final Map<String, Object> doc = getDocumentAsMap(request.bulkOperations().getFirst());
      return (Map<String, Object>) doc.get("value");
    }

    @Test
    void
        shouldIndexProcessInstanceMigrationWithoutBpmnProcessIdAndElementInstanceKeyOnPreviousVersion()
            throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceMigrationRecord()
                              .setProcessInstanceKey(1L)
                              .setTargetProcessDefinitionKey(99L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("bpmnProcessId")
          .doesNotContainKey("elementInstanceKey")
          .doesNotContainKey("rootProcessInstanceKey");
    }

    @Test
    void shouldIndexProcessInstanceMigrationWithBpmnProcessIdAndElementInstanceKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceMigrationRecord()
                              .setProcessInstanceKey(1L)
                              .setTargetProcessDefinitionKey(99L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("bpmnProcessId"))
          .describedAs(
              "Expect that migration records are serialized with bpmnProcessId on current version")
          .isEqualTo("my-process");
    }

    @Test
    void
        shouldIndexProcessInstanceModificationWithoutBpmnProcessIdAndElementInstanceKeyOnPreviousVersion()
            throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("bpmnProcessId")
          .doesNotContainKey("elementInstanceKey")
          .doesNotContainKey("rootProcessInstanceKey")
          .doesNotContainKey("moveInstructions");
    }

    @Test
    void shouldIndexProcessInstanceModificationWithBpmnProcessIdOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("bpmnProcessId"))
          .describedAs(
              "Expect that modification records are serialized with bpmnProcessId on current version")
          .isEqualTo("my-process");
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithoutElementInstanceKeyOnPreviousVersion(final ValueType valueType)
        throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("elementInstanceKey"))
          .describedAs(
              "Expect that records are serialized without elementInstanceKey on previous version")
          .isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ValueType.class,
        names = {
          "PROCESS_INSTANCE",
          "PROCESS_INSTANCE_CREATION",
        })
    void shouldIndexWithElementInstanceKeyOnCurrentVersion(final ValueType valueType)
        throws Exception {
      // given
      final var record =
          recordFactory.generateRecord(
              valueType, r -> r.withBrokerVersion(VersionUtil.getVersion()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("elementInstanceKey"))
          .describedAs(
              "Expect that records are serialized with elementInstanceKey on current version")
          .isNotNull();
    }

    @Test
    void shouldIndexVariableRecordWithoutElementInstanceKeyAndSourceOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.VARIABLE,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new VariableRecord()
                              .setName(BufferUtil.wrapString("varName"))
                              .setValue(BufferUtil.wrapString("\"varValue\""))
                              .setScopeKey(1L)
                              .setProcessInstanceKey(2L)
                              .setProcessDefinitionKey(3L)
                              .setRootProcessInstanceKey(100L)
                              .setSource(VariableSourceRecord.api())));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("elementInstanceKey")
          .doesNotContainKey("source")
          .doesNotContainKey("rootProcessInstanceKey");
    }

    @Test
    void shouldIndexVariableRecordWithElementInstanceKeyAndSourceOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.VARIABLE,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new VariableRecord()
                              .setName(BufferUtil.wrapString("varName"))
                              .setValue(BufferUtil.wrapString("\"varValue\""))
                              .setScopeKey(1L)
                              .setProcessInstanceKey(2L)
                              .setProcessDefinitionKey(3L)
                              .setRootProcessInstanceKey(100L)
                              .setSource(VariableSourceRecord.api())));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);

      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value).containsKey("source").containsKey("rootProcessInstanceKey");
    }

    @Test
    void shouldIndexJobBatchRecordWithoutTenantFilterOnPreviousVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new JobBatchRecord()
                              .setType("test-type")
                              .setTenantFilter(TenantFilter.PROVIDED)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("tenantFilter"))
          .describedAs(
              "Expect that job batch records are serialized without tenantFilter on previous version")
          .isNull();
    }

    @Test
    void shouldIndexJobBatchRecordWithTenantFilterOnCurrentVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new JobBatchRecord()
                              .setType("test-type")
                              .setTenantFilter(TenantFilter.PROVIDED)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("tenantFilter"))
          .describedAs(
              "Expect that job batch records are serialized with tenantFilter on current version")
          .isEqualTo(TenantFilter.PROVIDED.name());
    }

    @Test
    void
        shouldIndexJobBatchRecordWithoutRootProcessInstanceKeyAndJobToUserTaskMigrationInNestedJobsOnPreviousVersion()
            throws IOException {
      // given - the JobRecordValue mixin should also suppress rootProcessInstanceKey and
      // jobToUserTaskMigration inside the nested jobs list of a JobBatchRecord
      final var jobBatchRecord = new JobBatchRecord().setType("test-type");
      jobBatchRecord
          .jobs()
          .add()
          .setType("test-job")
          .setRootProcessInstanceKey(42L)
          .setIsJobToUserTaskMigration(true);
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getPreviousVersion()).withValue(jobBatchRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      final var jobs = (List<Map<String, Object>>) value.get("jobs");
      assertThat(jobs).isNotNull().isNotEmpty();
      assertThat(jobs.getFirst())
          .doesNotContainKey("rootProcessInstanceKey")
          .doesNotContainKey("jobToUserTaskMigration");
    }

    @Test
    void
        shouldIndexJobBatchRecordWithRootProcessInstanceKeyAndJobToUserTaskMigrationInNestedJobsOnCurrentVersion()
            throws IOException {
      // given
      final var jobBatchRecord = new JobBatchRecord().setType("test-type");
      jobBatchRecord
          .jobs()
          .add()
          .setType("test-job")
          .setRootProcessInstanceKey(42L)
          .setIsJobToUserTaskMigration(true);
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValue(jobBatchRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      final var jobs = (List<Map<String, Object>>) value.get("jobs");
      assertThat(jobs).isNotNull().isNotEmpty();
      assertThat(jobs.getFirst())
          .containsEntry("rootProcessInstanceKey", 42)
          .containsEntry("jobToUserTaskMigration", true);
    }

    @Test
    void shouldIndexUserTaskRecordWithoutTagsAndListenersConfigKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.USER_TASK,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(new UserTaskRecord().setUserTaskKey(1L).setTags(Set.of("tag1"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("tags")
          .doesNotContainKey("listenersConfigKey")
          .doesNotContainKey("rootProcessInstanceKey");
    }

    @Test
    void shouldIndexUserTaskRecordWithTagsOnCurrentVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.USER_TASK,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new UserTaskRecord().setUserTaskKey(1L).setTags(Set.of("tag1"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("tags"))
          .describedAs("Expect that user task records are serialized with tags on current version")
          .isNotNull();
    }

    @Test
    void shouldIndexDecisionRequirementsRecordWithoutDeploymentKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.DECISION_REQUIREMENTS,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new DecisionRequirementsRecord()
                              .setDecisionRequirementsId("drg-1")
                              .setDeploymentKey(99L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("deploymentKey"))
          .describedAs(
              "Expect that decision requirements records are serialized without deploymentKey on previous version")
          .isNull();
    }

    @Test
    void shouldIndexDecisionRequirementsRecordWithDeploymentKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.DECISION_REQUIREMENTS,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new DecisionRequirementsRecord()
                              .setDecisionRequirementsId("drg-1")
                              .setDeploymentKey(99L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("deploymentKey"))
          .describedAs(
              "Expect that decision requirements records are serialized with deploymentKey on current version")
          .isEqualTo(99);
    }

    @Test
    void shouldIndexDeploymentRecordWithoutDeploymentKeyOnPreviousVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.DEPLOYMENT,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(new DeploymentRecord().setDeploymentKey(42L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("deploymentKey"))
          .describedAs(
              "Expect that deployment records are serialized without deploymentKey on previous version")
          .isNull();
    }

    @Test
    void shouldIndexDeploymentRecordWithDeploymentKeyOnCurrentVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.DEPLOYMENT,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new DeploymentRecord().setDeploymentKey(42L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("deploymentKey"))
          .describedAs(
              "Expect that deployment records are serialized with deploymentKey on current version")
          .isEqualTo(42);
    }

    @Test
    void shouldIndexResourceDeletionRecordWithoutNewFieldsOnPreviousVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ResourceDeletionRecord()
                              .setResourceKey(1L)
                              .setDeleteHistory(true)
                              .setBatchOperationKey(100L)
                              .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
                              .setResourceType(ResourceType.PROCESS_DEFINITION)
                              .setResourceId("my-process")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("deleteHistory")
          .doesNotContainKey("batchOperationKey")
          .doesNotContainKey("batchOperationType")
          .doesNotContainKey("resourceType")
          .doesNotContainKey("resourceId");
    }

    @Test
    void shouldIndexResourceDeletionRecordWithNewFieldsOnCurrentVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ResourceDeletionRecord()
                              .setResourceKey(1L)
                              .setDeleteHistory(true)
                              .setBatchOperationKey(100L)
                              .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
                              .setResourceType(ResourceType.PROCESS_DEFINITION)
                              .setResourceId("my-process")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .containsKey("deleteHistory")
          .containsKey("batchOperationKey")
          .containsKey("batchOperationType")
          .containsKey("resourceType")
          .containsKey("resourceId");
    }

    @Test
    void shouldIndexMessageCorrelationRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new MessageCorrelationRecord()
                              .setName("msg")
                              .setCorrelationKey("key")
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(99L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that message correlation records are serialized without processDefinitionKey on previous version")
          .isNull();
    }

    @Test
    void shouldIndexMessageCorrelationRecordWithProcessDefinitionKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new MessageCorrelationRecord()
                              .setName("msg")
                              .setCorrelationKey("key")
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(99L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that message correlation records are serialized with processDefinitionKey on current version")
          .isEqualTo(99);
    }

    @Test
    void shouldIndexProcessInstanceBatchRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceBatchRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(88L)
                              .setBatchElementInstanceKey(5L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that process instance batch records are serialized without processDefinitionKey on previous version")
          .isNull();
    }

    @Test
    void shouldIndexProcessInstanceBatchRecordWithProcessDefinitionKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceBatchRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(88L)
                              .setBatchElementInstanceKey(5L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that process instance batch records are serialized with processDefinitionKey on current version")
          .isEqualTo(88);
    }

    @Test
    void shouldIndexRuntimeInstructionRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new RuntimeInstructionRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(77L)
                              .setElementId("task-1")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that runtime instruction records are serialized without processDefinitionKey on previous version")
          .isNull();
    }

    @Test
    void shouldIndexRuntimeInstructionRecordWithProcessDefinitionKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new RuntimeInstructionRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(77L)
                              .setElementId("task-1")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that runtime instruction records are serialized with processDefinitionKey on current version")
          .isEqualTo(77);
    }

    @Test
    void
        shouldIndexProcessInstanceMigrationRecordWithoutProcessDefinitionKeyAndTenantIdOnPreviousVersion()
            throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceMigrationRecord()
                              .setProcessInstanceKey(1L)
                              .setTargetProcessDefinitionKey(99L)
                              .setProcessDefinitionKey(55L)
                              .setTenantId("my-tenant")
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("processDefinitionKey")
          .doesNotContainKey("tenantId")
          .doesNotContainKey("bpmnProcessId")
          .doesNotContainKey("rootProcessInstanceKey");
    }

    @Test
    void
        shouldIndexProcessInstanceMigrationRecordWithProcessDefinitionKeyAndTenantIdOnCurrentVersion()
            throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceMigrationRecord()
                              .setProcessInstanceKey(1L)
                              .setTargetProcessDefinitionKey(99L)
                              .setProcessDefinitionKey(55L)
                              .setTenantId("my-tenant")
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .containsEntry("processDefinitionKey", 55)
          .containsEntry("tenantId", "my-tenant");
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithoutProcessDefinitionKeyOnPreviousVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(66L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("processDefinitionKey")
          .doesNotContainKey("bpmnProcessId")
          .doesNotContainKey("rootProcessInstanceKey");
    }

    @Test
    void shouldIndexProcessInstanceModificationRecordWithProcessDefinitionKeyOnCurrentVersion()
        throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new ProcessInstanceModificationRecord()
                              .setProcessInstanceKey(1L)
                              .setProcessDefinitionKey(66L)
                              .setBpmnProcessId("my-process")
                              .setRootProcessInstanceKey(100L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("processDefinitionKey"))
          .describedAs(
              "Expect that modification records are serialized with processDefinitionKey on current version")
          .isEqualTo(66);
    }

    @Test
    void shouldIndexDecisionEvaluationRecordWithoutDecisionEvaluationInstanceKeyOnPreviousVersion()
        throws IOException {
      // given - EvaluatedDecisionMixin suppresses decisionEvaluationInstanceKey in nested objects;
      // we must construct a record with evaluatedDecisions populated
      final var decisionEvaluationRecord = new DecisionEvaluationRecord();
      decisionEvaluationRecord
          .evaluatedDecisions()
          .add()
          .setDecisionId("decision-1")
          .setDecisionName("Decision 1")
          .setDecisionKey(1L)
          .setDecisionVersion(1)
          .setDecisionType("DECISION_TABLE")
          .setDecisionOutput(BufferUtil.wrapString("\"output\""))
          .setDecisionEvaluationInstanceKey("eval-key-1");
      final var record =
          recordFactory.generateRecord(
              ValueType.DECISION_EVALUATION,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(decisionEvaluationRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      final var evaluatedDecisions = (List<Map<String, Object>>) value.get("evaluatedDecisions");
      assertThat(evaluatedDecisions).isNotNull().isNotEmpty();
      assertThat(evaluatedDecisions.getFirst())
          .describedAs(
              "Expect that evaluatedDecisions are serialized without decisionEvaluationInstanceKey on previous version")
          .doesNotContainKey("decisionEvaluationInstanceKey");
    }

    @Test
    void shouldIndexDecisionEvaluationRecordWithDecisionEvaluationInstanceKeyOnCurrentVersion()
        throws IOException {
      // given - decisionEvaluationInstanceKey is @JsonIgnore on the impl class, so it will
      // always be null in the serialized output regardless of version. The EvaluatedDecisionMixin
      // on the previous-version mapper is for immutable values (generated via ProtocolFactory).
      final var decisionEvaluationRecord = new DecisionEvaluationRecord();
      decisionEvaluationRecord
          .evaluatedDecisions()
          .add()
          .setDecisionId("decision-1")
          .setDecisionName("Decision 1")
          .setDecisionKey(1L)
          .setDecisionVersion(1)
          .setDecisionType("DECISION_TABLE")
          .setDecisionOutput(BufferUtil.wrapString("\"output\""))
          .setDecisionEvaluationInstanceKey("eval-key-1");
      final var record =
          recordFactory.generateRecord(
              ValueType.DECISION_EVALUATION,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(decisionEvaluationRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then - decisionEvaluationInstanceKey is always null because it has @JsonIgnore on the impl
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      final var evaluatedDecisions = (List<Map<String, Object>>) value.get("evaluatedDecisions");
      assertThat(evaluatedDecisions).isNotNull().isNotEmpty();
      assertThat(evaluatedDecisions.getFirst().get("decisionEvaluationInstanceKey"))
          .describedAs(
              "Expect that decisionEvaluationInstanceKey is suppressed via @JsonIgnore on the impl class")
          .isNull();
    }

    @Test
    void shouldIndexCheckpointRecordWithoutFirstLogPositionOnPreviousVersion() throws IOException {
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
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value)
          .doesNotContainKey("checkpointType")
          .doesNotContainKey("checkpointTimestamp")
          .doesNotContainKey("firstLogPosition");
    }

    @Test
    void shouldIndexCheckpointRecordWithFirstLogPositionOnCurrentVersion() throws IOException {
      // given
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
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value).containsKey("checkpointType").containsKey("firstLogPosition");
    }

    @Test
    void shouldIndexUserTaskRecordWithListenersConfigKeyOnCurrentVersion() throws IOException {
      // given
      final var record =
          recordFactory.generateRecord(
              ValueType.USER_TASK,
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new UserTaskRecord().setUserTaskKey(1L).setListenersConfigKey(42L)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value.get("listenersConfigKey"))
          .describedAs(
              "Expect that user task records are serialized with listenersConfigKey on current version")
          .isEqualTo(42);
    }

    @Test
    void
        shouldIndexDeploymentRecordWithoutDeploymentKeyInNestedDecisionRequirementsMetadataOnPreviousVersion()
            throws IOException {
      // given - DecisionRequirementsMixin must suppress deploymentKey both at the top level
      // and inside the nested decisionRequirementsMetadata list
      final var deploymentRecord = new DeploymentRecord().setDeploymentKey(42L);
      deploymentRecord
          .decisionRequirementsMetadata()
          .add()
          .setDecisionRequirementsId("drg-1")
          .setDeploymentKey(42L);
      final var record =
          recordFactory.generateRecord(
              ValueType.DEPLOYMENT,
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(deploymentRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value).doesNotContainKey("deploymentKey");
      final var metadata = (List<Map<String, Object>>) value.get("decisionRequirementsMetadata");
      assertThat(metadata).isNotNull().isNotEmpty();
      assertThat(metadata.getFirst()).doesNotContainKey("deploymentKey");
    }

    @Test
    void
        shouldIndexDeploymentRecordWithDeploymentKeyInNestedDecisionRequirementsMetadataOnCurrentVersion()
            throws IOException {
      // given
      final var deploymentRecord = new DeploymentRecord().setDeploymentKey(42L);
      deploymentRecord
          .decisionRequirementsMetadata()
          .add()
          .setDecisionRequirementsId("drg-1")
          .setDeploymentKey(42L);
      final var record =
          recordFactory.generateRecord(
              ValueType.DEPLOYMENT,
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValue(deploymentRecord));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.getFirst(), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations()).hasSize(1);
      final Map<String, Object> value = getValueFromFirstOperation(request);
      assertThat(value).containsEntry("deploymentKey", 42);
      final var metadata = (List<Map<String, Object>>) value.get("decisionRequirementsMetadata");
      assertThat(metadata).isNotNull().isNotEmpty();
      assertThat(metadata.getFirst()).containsEntry("deploymentKey", 42);
    }
  }
}
