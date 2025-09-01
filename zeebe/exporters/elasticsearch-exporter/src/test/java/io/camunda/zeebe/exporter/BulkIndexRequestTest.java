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
import io.camunda.zeebe.exporter.BulkIndexRequest.BulkOperation;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceCreationRuntimeInstructionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceResultRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
          .extracting(BulkOperation::metadata)
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
          .extracting(BulkOperation::metadata)
          .containsExactlyElementsOf(actions);
      assertThat(request.lastIndexedMetadata()).isEqualTo(actions.get(1));
      assertThat(request.isEmpty()).isFalse();
    }
  }

  @Nested
  final class SerializationTest {
    @Test
    void shouldIndexRecordSerialized() {
      // given - use an empty authorization for comparison, since the bulk request will remove it
      final var record =
          recordFactory.generateRecord(
              b -> b.withAuthorizations(Map.of()).withBrokerVersion(VersionUtil.getVersion()));
      final var action = new BulkIndexAction("index", "id", "routing");

      // when
      request.index(action, record, new RecordSequence(PARTITION_ID, 1));

      // then
      final var operations = request.bulkOperations();
      assertThat(operations)
          .hasSize(1)
          .map(BulkOperation::metadata, this::deserializeSource)
          .containsExactly(Tuple.tuple(action, record));
    }

    @Test
    void shouldWriteOperationsAsNDJson() throws IOException {
      // given - use an empty authorization for comparison, since the bulk request will remove it
      final var records =
          recordFactory
              .generateRecords(
                  b -> b.withAuthorizations(Map.of()).withBrokerVersion(VersionUtil.getVersion()))
              .limit(2)
              .toList();
      final var actions =
          List.of(
              new BulkIndexAction("index", "id", "routing"),
              new BulkIndexAction("index2", "id2", "routing2"));
      request.index(actions.get(0), records.get(0), new RecordSequence(PARTITION_ID, 1));
      request.index(actions.get(1), records.get(1), new RecordSequence(PARTITION_ID, 2));

      // when
      final byte[] serializedBuffer;
      try (final var output = new ByteArrayOutputStream()) {
        request.writeTo(output);
        serializedBuffer = output.toByteArray();
      }

      // then
      final List<Tuple> deserializedOutput = new ArrayList<>();
      try (final var input =
          new BufferedReader(new InputStreamReader(new ByteArrayInputStream(serializedBuffer)))) {
        deserializedOutput.add(
            deserializeOperation(input.readLine().getBytes(), input.readLine().getBytes()));
        deserializedOutput.add(
            deserializeOperation(input.readLine().getBytes(), input.readLine().getBytes()));
      }

      assertThat(deserializedOutput)
          .containsExactly(
              Tuple.tuple(actions.get(0), records.get(0)),
              Tuple.tuple(actions.get(1), records.get(1)));
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
    void shouldIndexRecordWithoutAuthorizations() {
      // given
      final var records =
          recordFactory
              .generateRecords(r -> r.withBrokerVersion(VersionUtil.getVersion()))
              .limit(1)
              .toList();

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), records.get(0), new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("authorizations"))
          .describedAs("Expect that the records are NOT serialized with authorizations")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithoutBatchOperationReference() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withBatchOperationReference(10L));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("batchOperationReference"))
          .describedAs("Expect that the records are NOT serialized with batchOperationReference")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithBatchOperationReference() {
      // given
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withBatchOperationReference(10L));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("batchOperationReference"))
          .describedAs("Expect that the records are serialized with batchOperationReference")
          .containsExactly(10);
    }

    @Test
    void shouldIndexRecordWithoutDeniedReason() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(new UserTaskRecord().setDeniedReason("denied")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deniedReason"))
          .describedAs("Expect that the records are NOT serialized with deniedReason")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithDeniedReason() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new UserTaskRecord().setDeniedReason("denied")));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deniedReason"))
          .describedAs("Expect that the records are serialized with deniedReason")
          .containsExactly("denied");
    }

    @ParameterizedTest
    @MethodSource("recordsSupportingTags")
    void shouldIndexRecordWithTags(final Record record) {
      // given
      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tags"))
          .describedAs("Expect that the records are serialized with tags")
          .containsExactly(List.of("t1", "t2"));
    }

    @ParameterizedTest
    @MethodSource("recordsSupportingTagsPreviousVersion")
    void shouldIndexRecordWithoutTags(final Record record) {
      // given
      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("tags"))
          .describedAs("Expect that the records are NOT serialized with tags")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithoutResult() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          new JobRecord().setResult(new JobResult().setDeniedReason("denied"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("result"))
          .describedAs("Expect that the records are NOT serialized with deniedReason")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithResult() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new JobRecord().setResult(new JobResult().setDeniedReason("denied"))));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("result"))
          .extracting(source -> ((Map<String, Object>) source).get("deniedReason"))
          .describedAs("Expect that the records are NOT serialized with deniedReason")
          .containsExactly("denied");
    }

    @Test
    void shouldIndexProcessInstanceCreationRecordWithoutRuntimeInstructions() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          ImmutableProcessInstanceCreationRecordValue.builder()
                              .withRuntimeInstructions(
                                  List.of(
                                      ImmutableProcessInstanceCreationRuntimeInstructionValue
                                          .builder()
                                          .withAfterElementId("afterElementId")
                                          .build()))
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("runtimeInstructions"))
          .describedAs("Expect that the records are NOT serialized with runtimeInstructions")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessInstanceCreationRecordWithRuntimeInstructions() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableProcessInstanceCreationRecordValue.builder()
                              .withRuntimeInstructions(
                                  List.of(
                                      ImmutableProcessInstanceCreationRuntimeInstructionValue
                                          .builder()
                                          .withAfterElementId("afterElementId")
                                          .build()))
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("runtimeInstructions"))
          .extracting(source -> ((List) source).getFirst())
          .extracting(source -> ((Map<String, Object>) source).get("afterElementId"))
          .describedAs("Expect that the records are serialized with runtimeInstructions")
          .containsExactly("afterElementId");
    }

    @Test
    void shouldIndexProcessInstanceRecordWithoutNewFields() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getPreviousVersion())
                      .withValue(
                          ImmutableProcessInstanceRecordValue.builder()
                              .withElementInstancePath(List.of(List.of(1L, 2L, 3L)))
                              .withProcessDefinitionPath(List.of(1L, 2L, 3L))
                              .withCallingElementPath(List.of(3, 4, 5))
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("elementInstancePath"))
          .describedAs("Expect that the records are NOT serialized with elementInstancePath")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionPath"))
          .describedAs("Expect that the records are NOT serialized with processDefinitionPath")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("callingElementPath"))
          .describedAs("Expect that the records are NOT serialized with callingElementPath")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessInstanceRecordWithNewFields() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableProcessInstanceRecordValue.builder()
                              .withElementInstancePath(List.of(List.of(1L, 2L, 3L)))
                              .withProcessDefinitionPath(List.of(1L, 2L, 3L))
                              .withCallingElementPath(List.of(3, 4, 5))
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("elementInstancePath"))
          .describedAs("Expect that the records are serialized with elementInstancePath")
          .containsExactly(List.of(List.of(1, 2, 3)));

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("processDefinitionPath"))
          .describedAs("Expect that the records are serialized with processDefinitionPath")
          .containsExactly(List.of(1, 2, 3));

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("callingElementPath"))
          .describedAs("Expect that the records are serialized with callingElementPath")
          .containsExactly(List.of(3, 4, 5));
    }

    private Record<?> deserializeSource(final BulkOperation operation) {
      try {
        return MAPPER.readValue(operation.source(), new TypeReference<>() {});
      } catch (final IOException e) {
        throw new UncheckedIOException(
            String.format("Failed to deserialize operation [%s] source", operation.metadata()), e);
      }
    }

    private Tuple deserializeOperation(final byte[] metadata, final byte[] source) {
      try {
        return Tuple.tuple(
            MAPPER.readValue(metadata, BulkIndexAction.class),
            MAPPER.readValue(source, new TypeReference<Record<?>>() {}));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    static Stream<Arguments> recordsSupportingTagsPreviousVersion() {
      final var version = VersionUtil.getPreviousVersion();
      return tagRecordArguments(version);
    }

    static Stream<Arguments> recordsSupportingTags() {
      final var version = VersionUtil.getVersion();
      return tagRecordArguments(version);
    }

    static Stream<Arguments> tagRecordArguments(final String version) {
      final ProtocolFactory recordFactory = new ProtocolFactory();
      final var tags = List.of("t1", "t2");
      return Stream.of(
          Arguments.of(
              recordFactory.generateRecord(
                  r ->
                      r.withBrokerVersion(version)
                          .withValue(
                              ImmutableProcessInstanceCreationRecordValue.builder()
                                  .withTags(tags)
                                  .build()))),
          Arguments.of(
              recordFactory.generateRecord(
                  r ->
                      r.withBrokerVersion(version)
                          .withValue(
                              ImmutableProcessInstanceRecordValue.builder()
                                  .withTags(tags)
                                  .build()))),
          Arguments.of(
              recordFactory.generateRecord(
                  r ->
                      r.withBrokerVersion(version)
                          .withValue(
                              ImmutableProcessInstanceResultRecordValue.builder()
                                  .withTags(tags)
                                  .build()))),
          Arguments.of(
              recordFactory.generateRecord(
                  r ->
                      r.withBrokerVersion(version)
                          .withValue(ImmutableJobRecordValue.builder().withTags(tags).build()))));
    }
  }
}
