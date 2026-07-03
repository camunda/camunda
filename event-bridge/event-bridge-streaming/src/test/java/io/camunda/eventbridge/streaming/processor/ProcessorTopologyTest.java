/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ProcessorTopologyTest {

  @Test
  void shouldFanOutForwardedValuesToEveryChild() {
    // given — a source that forwards each record, wired to two leaf collectors
    final Collecting left = new Collecting();
    final Collecting right = new Collecting();
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("src", new Forwarding())
            .processor("left", left, "src")
            .processor("right", right, "src")
            .build();

    // when
    topology.init();
    topology.process("a");
    topology.process("b");

    // then — both children saw every record
    assertThat(left.received).containsExactly("a", "b");
    assertThat(right.received).containsExactly("a", "b");
  }

  @Test
  void shouldRouteToNamedChildForBranching() {
    // given — a brancher that routes to child "even" or "odd" by parity
    final Collecting even = new Collecting();
    final Collecting odd = new Collecting();
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("branch", new Branching())
            .processor("even", even, "branch")
            .processor("odd", odd, "branch")
            .build();

    // when
    topology.init();
    List.of("1", "2", "3", "4").forEach(topology::process);

    // then — each record reached exactly one branch
    assertThat(even.received).containsExactly("2", "4");
    assertThat(odd.received).containsExactly("1", "3");
  }

  @Test
  void shouldFireWallClockPunctuatorAtItsInterval() {
    // given — a processor scheduling a 100ms wall-clock tick, over a controllable clock
    final AtomicLong clock = new AtomicLong(1_000);
    final Collecting sink = new Collecting();
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("tick", new Ticking(PunctuationType.WALL_CLOCK_TIME, 100))
            .processor("sink", sink, "tick")
            .wallClock(clock::get)
            .build();
    topology.init();

    // when — flushes advance the clock; the first flush only primes the schedule
    topology.flush(); // primes at t=1000
    clock.set(1_050);
    topology.flush(); // 50ms elapsed — no fire
    clock.set(1_100);
    topology.flush(); // 100ms elapsed — fire
    clock.set(1_250);
    topology.flush(); // 150ms elapsed — fire

    // then
    assertThat(sink.received).containsExactly("tick@1100", "tick@1250");
  }

  @Test
  void shouldFireStreamTimePunctuatorAsEventTimeAdvances() {
    // given — a 1000ms stream-time punctuator
    final Collecting sink = new Collecting();
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("tick", new Ticking(PunctuationType.STREAM_TIME, 1_000))
            .processor("sink", sink, "tick")
            .build();
    topology.init();

    // when — stream time advances; wall-clock flushes must not fire a stream-time punctuator
    topology.advanceStreamTime(10_000); // primes
    topology.flush();
    topology.advanceStreamTime(10_500); // +500 — no fire
    topology.advanceStreamTime(11_000); // +1000 — fire

    // then
    assertThat(sink.received).containsExactly("tick@11000");
  }

  @Test
  void shouldGiveAConnectedProcessorItsStoreAndRejectAnUnconnectedOne() {
    // given — a store connected only to "reader"
    final Object store = new Object();
    final CapturingStoreUser reader = new CapturingStoreUser("kv");
    final CapturingStoreUser stranger = new CapturingStoreUser("kv");
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("reader", reader)
            .processor("stranger", stranger, "reader")
            .addStateStore("kv", store, "reader")
            .build();

    // when / then — the connected processor resolves the store on init; the other is rejected
    reader.resolveOnInit = true;
    stranger.resolveOnInit = true;
    assertThatThrownBy(topology::init).isInstanceOf(IllegalArgumentException.class);

    // and — with only the connected processor resolving, init succeeds and hands back the store
    stranger.resolveOnInit = false;
    topology.init();
    assertThat(reader.resolved).isSameAs(store);
  }

  @Test
  void shouldRejectABranchToAnUnknownChild() {
    // given — a brancher whose only child is "even", asked to route an odd record
    final ProcessorTopology<String> topology =
        ProcessorTopology.<String>builder()
            .source("branch", new Branching())
            .processor("even", new Collecting(), "branch")
            .build();
    topology.init();

    // when / then — routing to the missing "odd" child fails loudly
    assertThatThrownBy(() -> topology.process("1")).isInstanceOf(IllegalArgumentException.class);
  }

  /** A terminal sink that records what it receives. */
  private static final class Collecting implements Processor<String, Void> {
    private final List<String> received = new ArrayList<>();

    @Override
    public void process(final String record) {
      received.add(record);
    }
  }

  /** Forwards every record unchanged to all children. */
  private static final class Forwarding implements Processor<String, String> {
    private ProcessorContext<String> context;

    @Override
    public void init(final ProcessorContext<String> context) {
      this.context = context;
    }

    @Override
    public void process(final String record) {
      context.forward(record);
    }
  }

  /** Routes each record to the "even" or "odd" child by parity. */
  private static final class Branching implements Processor<String, String> {
    private ProcessorContext<String> context;

    @Override
    public void init(final ProcessorContext<String> context) {
      this.context = context;
    }

    @Override
    public void process(final String record) {
      context.forward(record, Integer.parseInt(record) % 2 == 0 ? "even" : "odd");
    }
  }

  /** Schedules a punctuator that forwards a "tick@<time>" whenever it fires. */
  private static final class Ticking implements Processor<String, String> {
    private final PunctuationType type;
    private final long intervalMs;

    Ticking(final PunctuationType type, final long intervalMs) {
      this.type = type;
      this.intervalMs = intervalMs;
    }

    @Override
    public void init(final ProcessorContext<String> context) {
      context.schedule(
          Duration.ofMillis(intervalMs), type, timestamp -> context.forward("tick@" + timestamp));
    }

    @Override
    public void process(final String record) {}
  }

  /** Resolves a named store on init, optionally — to prove access control both ways. */
  private static final class CapturingStoreUser implements Processor<String, Void> {
    private final String storeName;
    private boolean resolveOnInit;
    private Object resolved;

    CapturingStoreUser(final String storeName) {
      this.storeName = storeName;
    }

    @Override
    public void init(final ProcessorContext<Void> context) {
      if (resolveOnInit) {
        resolved = context.getStateStore(storeName);
      }
    }

    @Override
    public void process(final String record) {}
  }
}
