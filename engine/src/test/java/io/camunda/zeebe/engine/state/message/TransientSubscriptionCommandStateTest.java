/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.engine.state.message.TransientSubscriptionCommandState.CommandEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransientSubscriptionCommandStateTest {

  private TransientSubscriptionCommandState sut;

  @BeforeEach
  public void setUp() {
    sut = new TransientSubscriptionCommandState();
  }

  @Test
  public void shouldReturnNoEntriesByDefault() {
    // when
    final var actual = sut.getEntriesBefore(Long.MAX_VALUE);

    // then
    assertThat(actual).isEmpty();
  }

  @Test
  public void shouldReturnEntriesBeforeDeadline() {
    // when
    final var expected = new CommandEntry(1, "message", 500);
    sut.add(expected);
    sut.add(new CommandEntry(2, "message", 2000));

    // when
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).containsExactly(expected);
  }

  @Test
  public void shouldReturnEntriesOrderedBySentTime() {
    // when
    final var first = new CommandEntry(1, "message", 500);
    final var second = new CommandEntry(2, "message", 600);
    final var third = new CommandEntry(3, "message", 700);

    sut.add(second);
    sut.add(first);
    sut.add(third);

    // when
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).containsExactly(first, second, third);
  }

  @Test
  public void shouldOverwriteExistingEntries() {
    // when
    final var first = new CommandEntry(1, "message", 500);
    final var second = new CommandEntry(1, "message", 600);

    sut.add(first);
    sut.add(second);

    // when
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).containsExactly(second);
  }

  @Test
  public void shouldAcceptEntriesWithTheSameSentTime() {
    // when
    sut.add(new CommandEntry(1, "message", 500));
    sut.add(new CommandEntry(2, "message", 500));

    // when
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).hasSize(2);
  }

  @Test
  public void shouldReturnEntriesBasedOnUpdatedSentTime() {
    // when
    sut.add(new CommandEntry(1, "message", 3000));
    sut.add(new CommandEntry(2, "message", 2000));

    // when
    final var expected = new CommandEntry(1, "message", 500);
    sut.updateCommandSentTime(expected);
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).containsExactly(expected);
  }

  @Test
  public void shouldNotReturnEntriesThatHaveBeenRemoved() {
    // when
    sut.add(new CommandEntry(1, "message", 500));
    sut.add(new CommandEntry(2, "message", 2000));

    // when
    sut.remove(new CommandEntry(1, "message", 500));
    final var actual = sut.getEntriesBefore(1000);

    // then
    assertThat(actual).isEmpty();
  }

  @Test
  public void shouldBeTolerantWhenRemovingEntriesThatDoNotExist() {
    // when + then
    assertThatNoException().isThrownBy(() -> sut.remove(new CommandEntry(1, "message", 500)));
  }

  @Test
  public void shouldBeTolerantWhenUpdatingEntriesThatDoNotExist() {
    // when + then
    assertThatNoException()
        .isThrownBy(() -> sut.updateCommandSentTime(new CommandEntry(1, "message", 500)));
  }
}
