/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.eventbridge.streaming.aggregate.LongRecordValue;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EnvelopePublisherTest {

  private record Sent(int partition, ShuffleEnvelope envelope) {}

  private final List<Sent> sent = new ArrayList<>();
  private final EnvelopeTransport transport =
      new EnvelopeTransport() {
        @Override
        public void send(final int factsPartition, final byte[] frame) {
          sent.add(new Sent(factsPartition, ShuffleEnvelopeCodec.decode(frame)));
        }

        @Override
        public void flush() {}
      };

  private static CellDelta cell(final int aggId) {
    return new CellDelta(aggId, 0L, new byte[] {(byte) aggId}, new byte[] {1});
  }

  @Test
  void shouldBatchPerSegmentAndAssignMonotonicChunks() {
    // given
    final EnvelopePublisher publisher = new EnvelopePublisher(transport, 1, 0L);

    // when two cells of segment 0 and one of segment 1 (same facts partition) are flushed
    publisher.add(0, 0L, 1, cell(10));
    publisher.add(0, 0L, 1, cell(11));
    publisher.add(0, 1L, 1, cell(12));
    publisher.flush();

    // then one envelope per segment, segment 0 batching both its cells, each chunk 0
    assertThat(sent).hasSize(2);
    assertThat(sent.get(0).envelope().segment()).isEqualTo(0L);
    assertThat(sent.get(0).envelope().chunk()).isZero();
    assertThat(sent.get(0).envelope().cells()).hasSize(2);
    assertThat(sent.get(1).envelope().segment()).isEqualTo(1L);
    assertThat(sent.get(1).envelope().chunk()).isZero();

    // when segment 0 is flushed again (another meter's seal of the same segment)
    sent.clear();
    publisher.add(0, 0L, 1, cell(13));
    publisher.flush();

    // then it gets the next chunk for that segment — monotonic, so the reducer dedups correctly
    assertThat(sent).singleElement().satisfies(s -> assertThat(s.envelope().chunk()).isEqualTo(1));
  }

  @Test
  void shouldBeANoOpWhenNothingBuffered() {
    new EnvelopePublisher(transport, 1, 0L).flush();
    assertThat(sent).isEmpty();
  }

  @Test
  void shouldFlushDeterministicallyBySegmentThenFactsPartition() {
    // given cells buffered in scrambled segment/facts-partition order
    final EnvelopePublisher publisher = new EnvelopePublisher(transport, 1, 0L);
    publisher.add(0, 2L, 2, cell(10));
    publisher.add(0, 0L, 3, cell(11));
    publisher.add(0, 2L, 1, cell(12));
    publisher.add(0, 0L, 1, cell(13));
    publisher.add(0, 1L, 2, cell(14));

    // when
    publisher.flush();

    // then: envelopes leave ordered by segment, then facts partition — replay-stable chunking
    assertThat(sent)
        .extracting(s -> s.envelope().segment(), Sent::partition)
        .containsExactly(tuple(0L, 1), tuple(0L, 3), tuple(1L, 2), tuple(2L, 1), tuple(2L, 2));
    // ... and each envelope of a segment gets the next monotonic chunk in that order
    assertThat(sent).extracting(s -> s.envelope().chunk()).containsExactly(0, 1, 0, 0, 1);
  }

  @Test
  void shouldNotGrowChunkCountersAcrossPrunedSegments() {
    // given a publisher flushing a long run of advancing segments, pruned at each "commit" the way
    // the owning stage task does once no stream can seal below the watermark's segment anymore
    final EnvelopePublisher publisher = new EnvelopePublisher(transport, 1, 0L);

    // when
    for (long segment = 0; segment < 100; segment++) {
      publisher.add(0, segment, 1, cell(10));
      publisher.flush();
      publisher.pruneChunkCountersBelow(segment);
    }

    // then: the counter map stays bounded instead of accreting one entry per segment ever flushed
    assertThat(publisher.trackedSegments()).isLessThanOrEqualTo(1);

    // ... and the retained (at-watermark) segment keeps its monotonic chunk across the prunes
    sent.clear();
    publisher.add(0, 99L, 1, cell(11));
    publisher.flush();
    assertThat(sent).singleElement().satisfies(s -> assertThat(s.envelope().chunk()).isEqualTo(1));
  }

  @Test
  void shouldEncodeAndPublishAMeterDeltaThroughTheShuffleSink() {
    // given the encode (per-meter forwarding sink) wired to the route/publish (shuffle sink node)
    // over a two-dimension grain, routing to a single facts partition
    final EnvelopePublisher publisher = new EnvelopePublisher(transport, 1, 0L);
    final ShuffleSinkProcessor shuffle = new ShuffleSinkProcessor(publisher, 1);
    final DimensionSchema grain =
        DimensionSchema.of(
            new DimensionColumn("region", DimensionType.STRING),
            new DimensionColumn("def", DimensionType.LONG));
    final ForwardingSegmentSink<Long> sink =
        new ForwardingSegmentSink<>(7, new DimensionKeyValue(grain), new LongRecordValue());
    sink.bind(shuffle::process);

    // when a sealed delta is emitted and flushed
    sink.emit(new Windowed<>(DimensionKey.of(grain, "EU", 100L), 60_000L), 0, 5L, 42L);
    shuffle.flush();

    // then it lands as one cell tagged with the meter's aggId and window
    assertThat(sent)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.partition()).isEqualTo(1);
              assertThat(s.envelope().segment()).isEqualTo(5L);
              assertThat(s.envelope().cells())
                  .singleElement()
                  .satisfies(
                      c -> {
                        assertThat(c.streamId()).isEqualTo(7);
                        assertThat(c.windowStart()).isEqualTo(60_000L);
                      });
            });
  }
}
