/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.BulkIndexRequest.IndexOperation;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

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
    void
        shouldIndexDecisionEvaluationRecordWithoutDecisionEvaluationInstanceKeyOnPreviousVersion() {
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
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("evaluatedDecisions"))
          .extracting(source -> ((List<Object>) source).getFirst())
          .extracting(source -> ((Map<String, Object>) source).get("decisionEvaluationInstanceKey"))
          .describedAs(
              "Expect that evaluatedDecisions are serialized without decisionEvaluationInstanceKey on previous version")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexDecisionEvaluationRecordWithDecisionEvaluationInstanceKeyOnCurrentVersion() {
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
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("evaluatedDecisions"))
          .extracting(source -> ((List<Object>) source).getFirst())
          .extracting(source -> ((Map<String, Object>) source).get("decisionEvaluationInstanceKey"))
          .describedAs(
              "Expect that decisionEvaluationInstanceKey is suppressed via @JsonIgnore on the impl class")
          .containsExactly(new Object[] {null});
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
