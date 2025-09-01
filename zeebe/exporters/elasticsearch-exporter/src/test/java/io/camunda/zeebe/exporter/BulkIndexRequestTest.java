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
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.zeebe.exporter.BulkIndexRequest.BulkOperation;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableCommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableFormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcessMetadataValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      new ObjectMapper()
          .registerModule(new ZeebeProtocolModule())
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

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
      // given
      final var record =
          recordFactory.generateRecord(b -> b.withBrokerVersion(VersionUtil.getVersion()));
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
      // given
      final var records =
          recordFactory
              .generateRecords(b -> b.withBrokerVersion(VersionUtil.getVersion()))
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

    @ParameterizedTest
    @MethodSource("versionProvider")
    void shouldIndexRecordWithSequence(final String brokerVersion) {
      // given
      final var records =
          recordFactory.generateRecords(r -> r.withBrokerVersion(brokerVersion)).limit(2).toList();

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

    static Stream<Arguments> versionProvider() {
      return Stream.of(Arguments.of(VersionUtil.getVersion()), Arguments.of("8.5.0"));
    }

    @Test
    void shouldIndexRecordForPreviousVersionWithoutOperationReference() {
      // given
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion("8.5.0").withOperationReference(123L));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("operationReference"))
          .describedAs("Expect that the records are NOT serialized with operationReference")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexRecordWithOperationReference() {
      // given
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withOperationReference(123L));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("operationReference"))
          .describedAs("Expect that the records are serialized with operationReference")
          .containsExactly(123);
    }

    @Test
    void shouldIndexCommandDistributionRecordForPreviousVersionWithoutQueueId() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableCommandDistributionRecordValue.builder()
                              .withQueueId("test-queue-id")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("queueId"))
          .describedAs("Expect that the records are NOT serialized with queueId")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexCommandDistributionRecordWithQueueId() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableCommandDistributionRecordValue.builder()
                              .withQueueId("test-queue-id")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("queueId"))
          .describedAs("Expect that the records are serialized with queueId")
          .containsExactly("test-queue-id");
    }

    @Test
    void shouldIndexDecisionRecordForPreviousVersionWithoutDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableDecisionRecordValue.builder()
                              .withDeploymentKey(456L)
                              .withVersionTag("v1.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are NOT serialized with deploymentKey")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are NOT serialized with versionTag")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexDecisionRecordWithDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableDecisionRecordValue.builder()
                              .withDeploymentKey(456L)
                              .withVersionTag("v1.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized with deploymentKey")
          .containsExactly(456);

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are serialized with versionTag")
          .containsExactly("v1.0");
    }

    @Test
    void shouldIndexDeploymentRecordForPreviousVersionWithoutDeploymentKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableDeploymentRecordValue.builder()
                              .withDeploymentKey(789L)
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are NOT serialized with deploymentKey")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexDeploymentRecordWithDeploymentKey() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableDeploymentRecordValue.builder()
                              .withDeploymentKey(789L)
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized with deploymentKey")
          .containsExactly(789);
    }

    @Test
    void shouldIndexFormMetadataRecordForPreviousVersionWithoutDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableFormMetadataValue.builder()
                              .withDeploymentKey(321L)
                              .withVersionTag("v2.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are NOT serialized with deploymentKey")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are NOT serialized with versionTag")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexFormMetadataRecordWithDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableFormMetadataValue.builder()
                              .withDeploymentKey(321L)
                              .withVersionTag("v2.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized with deploymentKey")
          .containsExactly(321);

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are serialized with versionTag")
          .containsExactly("v2.0");
    }

    @Test
    void shouldIndexProcessMetadataRecordForPreviousVersionWithoutDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableProcessMetadataValue.builder()
                              .withDeploymentKey(654L)
                              .withVersionTag("v3.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are NOT serialized with deploymentKey")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are NOT serialized with versionTag")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexProcessMetadataRecordWithDeploymentKeyAndVersionTag() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableProcessMetadataValue.builder()
                              .withDeploymentKey(654L)
                              .withVersionTag("v3.0")
                              .build()));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("deploymentKey"))
          .describedAs("Expect that the records are serialized with deploymentKey")
          .containsExactly(654);

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("versionTag"))
          .describedAs("Expect that the records are serialized with versionTag")
          .containsExactly("v3.0");
    }

    @Test
    void shouldIndexUserTaskRecordForPreviousVersionWithoutPriority() {
      // given
      final var record =
          recordFactory.generateRecord(
              r -> r.withBrokerVersion("8.5.0").withValue(new UserTaskRecord().setPriority(50)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("priority"))
          .describedAs("Expect that the records are NOT serialized with priority")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexUserTaskRecordWithPriority() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(new UserTaskRecord().setPriority(50)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("priority"))
          .describedAs("Expect that the records are serialized with priority")
          .containsExactly(50);
    }

    @Test
    void
        shouldIndexIncidentRecordForPreviousVersionWithoutElementInstancePathProcessDefinitionPathAndCallingElementPath() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          ImmutableIncidentRecordValue.builder()
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
    void
        shouldIndexIncidentRecordWithElementInstancePathProcessDefinitionPathAndCallingElementPath() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          ImmutableIncidentRecordValue.builder()
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

    @Test
    void shouldIndexJobRecordForPreviousVersionWithoutJobListenerEventTypeAndChangedAttributes() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion("8.5.0")
                      .withValue(
                          new JobRecord()
                              .setChangedAttributes(Set.of("attr1", "attr2"))
                              .setListenerEventType(JobListenerEventType.START)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("jobListenerEventType"))
          .describedAs("Expect that the records are NOT serialized with jobListenerEventType")
          .containsExactly(new Object[] {null});

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("changedAttributes"))
          .describedAs("Expect that the records are NOT serialized with changedAttributes")
          .containsExactly(new Object[] {null});
    }

    @Test
    void shouldIndexJobRecordWithJobListenerEventTypeAndChangedAttributes() {
      // given
      final var record =
          recordFactory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValue(
                          new JobRecord()
                              .setChangedAttributes(Set.of("attr1", "attr2"))
                              .setListenerEventType(JobListenerEventType.START)));

      final var actions = List.of(new BulkIndexAction("index", "id", "routing"));

      // when
      request.index(actions.get(0), record, new RecordSequence(PARTITION_ID, 10));

      // then
      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("jobListenerEventType"))
          .describedAs("Expect that the records are serialized with jobListenerEventType")
          .containsExactly(JobListenerEventType.START.toString());

      assertThat(request.bulkOperations())
          .hasSize(1)
          .map(operation -> MAPPER.readValue(operation.source(), MAP_TYPE_REFERENCE))
          .extracting(source -> source.get("value"))
          .extracting(source -> ((Map<String, Object>) source).get("changedAttributes"))
          .flatMap(source -> new HashSet<>((Collection) source))
          .describedAs("Expect that the records are serialized with changedAttributes")
          .containsExactlyInAnyOrder("attr1", "attr2");
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
  }
}
