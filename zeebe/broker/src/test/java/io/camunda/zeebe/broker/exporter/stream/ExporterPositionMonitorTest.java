/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ExporterPositionMonitorTest {

  private final FlowControl flowControl = mock(FlowControl.class);
  private final ExporterPositionMonitor exportedPositions =
      new ExporterPositionMonitor(flowControl);

  @Test
  void shouldNotNotifyFlowControlWhenNoExporterIsTracked() {
    // given - no exporter has been recovered or exported yet

    // when / then - nothing to report, so FlowControl must not be touched at all
    verify(flowControl, never()).onExported(anyLong());
  }

  @Test
  void shouldReportTheSlowestExportersPositionAsTheMinimum() {
    // given
    exportedPositions.recover("fast", 5L);
    exportedPositions.recover("slow", 2L);

    // when
    exportedPositions.onExported("fast", 10L);

    // then - the slow exporter hasn't caught up yet, so the reported minimum stays at its position
    assertThat(lastReportedPosition()).isEqualTo(2L);
  }

  @Test
  void shouldAdvanceTheMinimumOnceTheSlowestExporterCatchesUp() {
    // given
    exportedPositions.recover("fast", 5L);
    exportedPositions.recover("slow", 2L);
    exportedPositions.onExported("fast", 10L);

    // when
    exportedPositions.onExported("slow", 7L);

    // then
    assertThat(lastReportedPosition()).isEqualTo(7L);
  }

  @Test
  void shouldExcludeARemovedExporterFromTheMinimum() {
    // given
    exportedPositions.recover("fast", 5L);
    exportedPositions.recover("slow", 2L);
    exportedPositions.onExported("fast", 10L);

    // when - the slow exporter is disabled/removed
    exportedPositions.remove("slow");

    // then - the minimum is now only over the remaining exporter
    assertThat(lastReportedPosition()).isEqualTo(10L);
  }

  @Test
  void shouldReportNonPositivePositionWhileAnExporterHasNotYetExportedAnything() {
    // given - a freshly-started exporter with no persisted position yet (VALUE_NOT_FOUND == -1)
    exportedPositions.recover("brandNew", -1L);
    exportedPositions.recover("caughtUp", 42L);

    // when
    exportedPositions.onExported("caughtUp", 100L);

    // then - FlowControl itself ignores non-positive positions, so it's still called with -1
    // rather than silently skipping the caught-up exporter's report; this matches the
    // pre-decoupling behavior of not throttling until every exporter has exported at least once.
    assertThat(lastReportedPosition()).isEqualTo(-1L);
  }

  private long lastReportedPosition() {
    final var captor = ArgumentCaptor.forClass(Long.class);
    verify(flowControl, atLeastOnce()).onExported(captor.capture());
    return captor.getValue();
  }
}
