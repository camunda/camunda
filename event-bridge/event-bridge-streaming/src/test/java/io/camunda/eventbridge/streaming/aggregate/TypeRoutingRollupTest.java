/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TypeRoutingRollupTest {

  private interface Fact {}

  private record A(int v) implements Fact {}

  private record B(int v) implements Fact {}

  /** A rollup that just records what it accepted. */
  private static final class Recording<T> implements Rollup<T> {
    final List<T> accepted = new ArrayList<>();

    @Override
    public void accept(final T fact) {
      accepted.add(fact);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  @Test
  void shouldRouteOnlyMatchingSubtype() {
    // given — a fan-out of mixed facts routed to an A-only rollup
    final Recording<A> aRollup = new Recording<>();
    final Rollup<Fact> routed = new TypeRoutingRollup<>(A.class, aRollup);

    // when
    routed.accept(new A(1));
    routed.accept(new B(2)); // ignored
    routed.accept(new A(3));

    // then
    assertThat(aRollup.accepted).containsExactly(new A(1), new A(3));
  }
}
