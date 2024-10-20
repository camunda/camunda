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
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ScaleUpTest {
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void beforeEach() {
    RecordingExporter.reset();
    clearInvocations(engine.getCommandResponseWriter());
  }

  @Test
  public void shouldRespondToScaleUp() {
    // given
    initRoutingState();
    final var command =
        RecordToWrite.command()
            .scale(
                ScaleIntent.SCALE_UP,
                new ScaleRecord().setCurrentPartitionCount(1).setDesiredPartitionCount(3));

    // when
    engine.writeRecords(command);

    // then
    verify(engine.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldFinishScaling() {
    // given
    initRoutingState();
    final var command =
        RecordToWrite.command()
            .scale(
                ScaleIntent.SCALE_UP,
                new ScaleRecord().setCurrentPartitionCount(1).setDesiredPartitionCount(3));

    // when
    engine.writeRecords(command);

    // then
    assertThat(
            RecordingExporter.scaleRecords()
                .limit(r -> r.getIntent() == ScaleIntent.SCALED_UP)
                .map(Record::getIntent))
        .containsExactly(ScaleIntent.SCALE_UP, ScaleIntent.SCALING_UP, ScaleIntent.SCALED_UP);
  }

  @Test
  public void shouldRejectWithoutRoutingState() {
    // given
    final var command =
        RecordToWrite.command()
            .scale(
                ScaleIntent.SCALE_UP,
                new ScaleRecord().setCurrentPartitionCount(1).setDesiredPartitionCount(3));

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
    initRoutingState();
    final var command = RecordToWrite.command().scale(ScaleIntent.SCALE_UP, new ScaleRecord());

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo(
                      "Desired partition count must be greater than current partition count");
            });
  }

  @Test
  public void shouldRejectScaleUpWithInvalidPartitionCount() {
    // given
    initRoutingState();
    final var command =
        RecordToWrite.command()
            .scale(
                ScaleIntent.SCALE_UP,
                new ScaleRecord().setCurrentPartitionCount(1).setDesiredPartitionCount(10000));

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
    initRoutingState();
    final var command =
        RecordToWrite.command()
            .scale(
                ScaleIntent.SCALE_UP,
                new ScaleRecord().setCurrentPartitionCount(3).setDesiredPartitionCount(1));

    // when
    engine.writeRecords(command);

    // then
    assertThat(RecordingExporter.scaleRecords().onlyCommandRejections().findFirst())
        .hasValueSatisfying(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
              assertThat(rejection.getRejectionReason())
                  .isEqualTo(
                      "Desired partition count must be greater than current partition count");
            });
  }

  private void initRoutingState() {
    ((MutableRoutingState) engine.getProcessingState().getRoutingState()).initializeRoutingInfo(1);
  }
}
