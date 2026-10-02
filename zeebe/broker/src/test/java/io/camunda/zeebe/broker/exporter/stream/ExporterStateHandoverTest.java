/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.protocol.ExporterPosition;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ExporterStateHandoverTest {

  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  private final ExporterStateHandover handover = new ExporterStateHandover();

  @Test
  void shouldKeepThePreviousMetadataWhenNoneIsSet() {
    // given
    handover.onStateSet(EXPORTER_ID_1, 10, BufferUtil.wrapString("metadata"));

    // when
    handover.onStateSet(EXPORTER_ID_1, 20, null);

    // then
    assertThat(handover.outgoing()).containsExactly(position(EXPORTER_ID_1, 20, "metadata"));
  }

  @Test
  void shouldLowerThePosition() {
    // given
    handover.onStateSet(EXPORTER_ID_1, 20, BufferUtil.wrapString("metadata"));

    // when
    handover.onPositionSet(EXPORTER_ID_1, 10);

    // then
    assertThat(handover.outgoing()).containsExactly(position(EXPORTER_ID_1, 10, "metadata"));
  }

  @Test
  void shouldDropARemovedExporter() {
    // given
    handover.onPositionSet(EXPORTER_ID_1, 10);
    handover.onPositionSet(EXPORTER_ID_2, 20);

    // when
    handover.onRemoved(EXPORTER_ID_1);

    // then
    assertThat(handover.outgoing())
        .extracting(ExporterPosition::exporterId)
        .containsExactly(EXPORTER_ID_2);
  }

  @Test
  void shouldHandOverEveryExportersPosition() {
    // given
    handover.onStateSet(EXPORTER_ID_1, 10, BufferUtil.wrapString("e1"));
    handover.onStateSet(EXPORTER_ID_2, 20, new UnsafeBuffer());

    // when
    final var outgoing = handover.outgoing();

    // then
    assertThat(outgoing)
        .containsExactlyInAnyOrder(
            position(EXPORTER_ID_1, 10, "e1"), position(EXPORTER_ID_2, 20, ""));
  }

  @Test
  void shouldHandOverNothingOnceCleared() {
    // given
    handover.onPositionSet(EXPORTER_ID_1, 10);

    // when
    handover.clearOutgoing();

    // then
    assertThat(handover.outgoing()).isEmpty();
  }

  @Test
  void shouldTakeTheIncomingPositionsForTheFollowingTerm() {
    // given
    handover.receive(4, List.of(position(EXPORTER_ID_1, 10, "e1")));

    // when
    final var taken = handover.takeIncoming(5);

    // then
    assertThat(taken)
        .containsOnlyKeys(EXPORTER_ID_1)
        .extractingByKey(EXPORTER_ID_1)
        .satisfies(
            entry -> {
              assertThat(entry.position()).isEqualTo(10);
              assertThat(BufferUtil.bufferAsString(entry.metadata())).isEqualTo("e1");
            });
    assertThat(handover.takeIncoming(5)).isEmpty();
  }

  @Test
  void shouldDiscardTheIncomingPositionsForAnotherTerm() {
    // given
    handover.receive(4, List.of(position(EXPORTER_ID_1, 10, "e1")));

    // when
    final var taken = handover.takeIncoming(6);

    // then
    assertThat(taken).isEmpty();
    assertThat(handover.takeIncoming(5)).isEmpty();
  }

  private static ExporterPosition position(
      final String exporterId, final long position, final String metadata) {
    return new ExporterPosition(exporterId, position, metadata.getBytes());
  }
}
