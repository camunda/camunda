/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.ScaleRecordStream;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ScaleUpTest {
  @Rule public final EngineRule engine = EngineRule.multiplePartition(2);
  private long key;

  @Before
  public void beforeEach() {
    RecordingExporter.reset();
    clearInvocations(engine.getCommandResponseWriter());
    key = Protocol.encodePartitionId(1, 0);
  }

  @Test
  public void shouldRespondToScaleUp() {
    // given
    final var command =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key);

    command.recordMetadata().requestId(123);

    // when
    engine.writeRecords(command);

    // then
    verify(engine.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldFinishScaling() {
    // given

    final var command =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key);

    final var bootstrapPartitions =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(3))
            .key(key);
    // when
    engine.writeRecords(command, bootstrapPartitions);

    // then
    assertThat(allScaleRecordsForPartition(1).map(Record::getIntent))
        .containsExactly(
            ScaleIntent.SCALE_UP,
            ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALING_UP,
            ScaleIntent.PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALED_UP);
    assertPartitionBootstrappedHasBeenRedistributed();
  }

  @Test
  public void shouldRejectWithoutRoutingState() {
    // given
    engine.stop();
    engine.withInitializeRoutingState(false);
    engine.start();
    final var command =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key);

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo(
                      "Routing state is not initialized, partition scaling is probably disabled.");
            });
  }

  @Test
  public void shouldRejectEmptyScaleUp() {
    // given
    final var command =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord()).key(key);

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo("Partition count must be at least 1");
            });
  }

  @Test
  public void shouldRejectScaleUpWithInvalidPartitionCount() {
    // given

    final var command =
        RecordToWrite.command()
            .scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(10000))
            .key(key);

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
              assertThat(rejection.getRejectionReason())
                  .startsWith("Partition count must be at most");
            });
  }

  @Test
  public void shouldRejectScaleDown() {
    // given
    final var command =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(1)).key(key);

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo(
                      "The desired partition count is smaller than the currently active partitions");
            });
  }

  @Test
  public void shouldRejectRedundantScaleUp() {
    // given - a scale up from 2 to 3 was already requested
    ((MutableRoutingState) engine.getProcessingState(1).getRoutingState())
        .setDesiredPartitions(Set.of(1, 2, 3), 999L);

    // when
    engine.writeRecords(
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key));

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo("The desired partition count was already requested");
            });
  }

  @Test
  public void shouldRespondWithTheCurrentNumberOfRedistributedPartitionsAndMessageCorrelation() {
    // given
    final var scaleUpCommand =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(4)).key(key);

    final var getStatusCommand =
        RecordToWrite.command().scale(ScaleIntent.STATUS, new ScaleRecord().status());
    // when
    final var highestPositionAppended = engine.writeRecords(scaleUpCommand, getStatusCommand);

    // then
    final var record =
        RecordingExporter.scaleRecords()
            .limit(r -> r.getIntent() == ScaleIntent.STATUS_RESPONSE)
            .getLast();
    assertThat(record.getValue().getDesiredPartitionCount()).isEqualTo(4);
    assertThat(record.getValue().getRedistributedPartitions()).containsExactly(1, 2);
    // SCALE_UP command is the first command we manually appended, meaning it is positioned 1 before
    // the status command
    assertThat(record.getValue().getScalingPosition()).isEqualTo(highestPositionAppended - 1);
    assertThat(record.getValue().getMessageCorrelationPartitions()).isEqualTo(2);

    // when the partitions are marked as bootstrapped
    final var bootstrapPartition3 =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(3));
    final var bootstrapPartition4 =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(4));
    engine.writeRecords(bootstrapPartition3, bootstrapPartition4, getStatusCommand);

    // then
    final var finalResponse =
        RecordingExporter.scaleRecords()
            .skipUntil(r -> r.getIntent() == ScaleIntent.PARTITION_BOOTSTRAPPED)
            .limit(r -> r.getIntent() == ScaleIntent.STATUS_RESPONSE)
            .getLast();
    assertThat(finalResponse.getValue().getDesiredPartitionCount()).isEqualTo(4);
    assertThat(finalResponse.getValue().getRedistributedPartitions()).containsExactly(1, 2, 3, 4);
    assertThat(record.getValue().getMessageCorrelationPartitions()).isEqualTo(2);
  }

  @Test
  public void shouldScaleUpContinuously() {
    // given
    final var index = 1023;
    final var scaleTo3 =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key);

    final var bootstrapPartitionsTo3 =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(3))
            .key(key);
    // when
    engine.writeRecords(scaleTo3, bootstrapPartitionsTo3);

    // then
    assertThat(allScaleRecordsForPartition(1).map(Record::getIntent))
        .hasSize(5)
        .containsSequence(
            ScaleIntent.SCALE_UP,
            ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALING_UP,
            ScaleIntent.PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALED_UP);

    assertPartitionBootstrappedHasBeenRedistributed();

    RecordingExporter.reset();

    // when scaling up again
    final var scaleTo4 =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(4)).key(key);
    final var bootstrapPartitionsTo4 =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(4))
            .key(key);
    engine.writeRecords(scaleTo4, bootstrapPartitionsTo4);

    // then
    assertThat(allScaleRecordsForPartition(1).map(Record::getIntent))
        .hasSize(5)
        .containsSequence(
            ScaleIntent.SCALE_UP,
            ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALING_UP,
            ScaleIntent.PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALED_UP);
    assertPartitionBootstrappedHasBeenRedistributed();
  }

  @Test
  public void bootstrappingAPartitionIsIdempotent() {
    // given
    final var index = 99;
    final var scaleTo3 =
        RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord().scaleUp(3)).key(key);

    final var bootstrapPartitionsTo3 =
        RecordToWrite.command()
            .scale(
                ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
                new ScaleRecord().markPartitionBootstrapped(3))
            .key(key);
    bootstrapPartitionsTo3.recordMetadata().requestId(1234);
    // when
    engine.writeRecords(scaleTo3, bootstrapPartitionsTo3);
    assertThat(allScaleRecordsForPartition(1).map(Record::getIntent)).hasSize(5);
    assertPartitionBootstrappedHasBeenRedistributed();

    RecordingExporter.reset();

    // when
    engine.writeRecords(bootstrapPartitionsTo3);

    // then
    // no additional events are added to the log stream
    final var scaleRecords =
        RecordingExporter.expectNoMatchingRecords(
            records ->
                records
                    .scaleRecords()
                    .limit(r -> r.getIntent() == ScaleIntent.SCALED_UP)
                    .filter(r -> r.getPartitionId() == 1));
    assertThat(scaleRecords)
        .hasSize(2)
        .map(Record::getIntent)
        .containsSequence(
            ScaleIntent.MARK_PARTITION_BOOTSTRAPPED, ScaleIntent.PARTITION_BOOTSTRAPPED);

    verify(engine.getCommandResponseWriter(), timeout(1000).times(2))
        .tryWriteResponse(anyInt(), anyLong());
  }

  private ScaleRecordStream allScaleRecordsForPartition(final int partitionId) {
    return RecordingExporter.scaleRecords()
        .limit(r -> r.getIntent() == ScaleIntent.SCALED_UP && r.getPartitionId() == partitionId)
        .filter(r -> r.getPartitionId() == partitionId);
  }

  private void assertPartitionBootstrappedHasBeenRedistributed() {
    assertThat(allScaleRecordsForPartition(2))
        .hasSize(5)
        .map(Record::getIntent)
        .containsExactly(
            ScaleIntent.SCALE_UP,
            ScaleIntent.SCALING_UP,
            ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
            ScaleIntent.PARTITION_BOOTSTRAPPED,
            ScaleIntent.SCALED_UP);
  }
}
