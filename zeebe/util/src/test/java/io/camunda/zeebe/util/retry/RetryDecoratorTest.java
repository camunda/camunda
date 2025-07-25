/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.agrona.LangUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryDecoratorTest {

  private RetryDecorator retryDecorator;

  @BeforeEach
  void setUp() {
    final var retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxRetries(2); // retry twice
    retryDecorator = new RetryDecorator(retryConfiguration);
  }

  @Test
  void shouldDecorateCheckedRunnableRetryOnSneakyThrows() {
    // given
    final Runnable runnable = mock(Runnable.class);
    doAnswer(
            a -> {
              LangUtil.rethrowUnchecked(new Exception("Sneaky throws"));
              return null;
            })
        .doAnswer(a -> null)
        .when(runnable)
        .run();
    // when
    retryDecorator.decorateCheckedRunnable("operation", () -> runnable.run());
    // then
    verify(runnable, times(2)).run();
  }

  @Test
  void shouldDecorateRunnableNotRetryOnSneakyThrows() {
    // given
    final Runnable runnable = mock(Runnable.class);
    doAnswer(
            a -> {
              LangUtil.rethrowUnchecked(new Exception("Sneaky throws"));
              return null;
            })
        .doAnswer(a -> null)
        .when(runnable)
        .run();

    // when
    final var exception =
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> retryDecorator.decorate("operation", runnable))
            .actual();

    // then
    verify(runnable, times(1)).run();
    assertThat(exception.getMessage()).isEqualTo("Sneaky throws");
  }
}
