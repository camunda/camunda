/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.segment.LogId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class RendezvousTest {

  private static final List<LogId> LOGS =
      IntStream.rangeClosed(1, 1000).mapToObj(p -> new LogId("c", "default", p)).toList();

  @Test
  void shouldGiveEveryWorkerRoughlyItsShare() {
    // given
    final var workers = List.of("w-1", "w-2", "w-3", "w-4");

    // when
    final var shares =
        LOGS.stream()
            .collect(
                Collectors.groupingBy(
                    log -> Rendezvous.owner(workers, log), Collectors.counting()));

    // then
    assertThat(shares).containsOnlyKeys(workers);
    assertThat(shares.values()).allSatisfy(count -> assertThat(count).isBetween(200L, 300L));
  }

  @Test
  void shouldAgreeWhateverTheOrderOfTheWorkers() {
    // given
    final var workers = List.of("w-1", "w-2", "w-3");

    // when / then
    for (final var log : LOGS) {
      assertThat(Rendezvous.owner(workers.reversed(), log))
          .isEqualTo(Rendezvous.owner(workers, log));
    }
  }

  @Test
  void shouldMoveOnlyTheLogsOfAWorkerThatComesOrGoes() {
    // given
    final var before = owners(List.of("w-1", "w-2", "w-3"));

    // when
    final var after = owners(List.of("w-1", "w-2", "w-3", "w-4"));

    // then
    for (final var log : LOGS) {
      if (!after.get(log).equals("w-4")) {
        assertThat(after.get(log)).isEqualTo(before.get(log));
      }
    }
    assertThat(after.values()).contains("w-4");
  }

  @Test
  void shouldNeedAWorker() {
    // when / then
    assertThatThrownBy(() -> Rendezvous.owner(List.of(), LOGS.getFirst()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected at least one worker to own log c/default/1, but got none");
  }

  private static Map<LogId, String> owners(final List<String> workers) {
    return LOGS.stream()
        .collect(Collectors.toMap(Function.identity(), log -> Rendezvous.owner(workers, log)));
  }
}
