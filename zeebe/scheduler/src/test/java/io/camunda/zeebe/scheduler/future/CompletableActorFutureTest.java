/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CompletableActorFutureTest {
  @Nested
  public class Catching {
    @Test
    public void shouldReturnExceptionalFutureWhenSupplierThrows() {
      // given
      final Supplier<ActorFuture<Void>> supplier =
          () -> {
            throw new RuntimeException("Expected");
          };

      // when
      final var future = CompletableActorFuture.catching(supplier);

      assertThat(future.getException()).hasMessage("Expected");
    }

    @Test
    public void shouldReturnFutureWhenSupplierCompletes() {
      // given
      final Supplier<ActorFuture<String>> supplier =
          () -> CompletableActorFuture.completed("value");

      // when
      final var future = CompletableActorFuture.catching(supplier);

      // then
      assertThat(future.join()).isEqualTo("value");
    }
  }
}
