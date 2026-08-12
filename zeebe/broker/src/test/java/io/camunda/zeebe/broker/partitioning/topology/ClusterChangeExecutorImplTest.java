/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ClusterChangeExecutorImplTest {

  @Nested
  class PreScalingTest {

    @Test
    void shouldCallNodeIdProviderScale() {
      // given
      final var nodeIdProvider = mock(NodeIdProvider.class);
      when(nodeIdProvider.scale(anyInt())).thenReturn(CompletableFuture.completedFuture(null));
      final var executor =
          new ClusterChangeExecutorImpl(
              new TestConcurrencyControl(), nodeIdProvider, Optional.empty());

      // when
      final var result =
          executor.preScaling(
              1, Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // then
      Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
      verify(nodeIdProvider, times(1)).scale(3);
    }

    @Test
    void shouldOnlyCallNodeIdProviderScaleWhenScalingUp() {
      // given
      final var nodeIdProvider = mock(NodeIdProvider.class);
      when(nodeIdProvider.scale(anyInt())).thenReturn(CompletableFuture.completedFuture(null));
      final var executor =
          new ClusterChangeExecutorImpl(
              new TestConcurrencyControl(), nodeIdProvider, Optional.empty());

      // when
      final var result = executor.preScaling(3, Set.of(MemberId.from("0"), MemberId.from("1")));

      // then
      Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
      verify(nodeIdProvider, times(0)).scale(anyInt());
    }

    @Test
    void shouldCompleteExceptionallyWhenNodeIdProviderScaleFails() {
      // given
      final var nodeIdProvider = mock(NodeIdProvider.class);
      when(nodeIdProvider.scale(anyInt()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("scale failed")));
      final var executor =
          new ClusterChangeExecutorImpl(
              new TestConcurrencyControl(), nodeIdProvider, Optional.empty());

      // when
      final var result =
          executor.preScaling(
              1, Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // then
      Assertions.assertThat(result)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining("scale failed");
      verify(nodeIdProvider, times(1)).scale(3);
    }
  }

  @Nested
  class PostScalingTest {

    @Test
    void shouldCallNodeIdProviderScaleOnPostScale() {
      // given
      final var nodeIdProvider = mock(NodeIdProvider.class);
      when(nodeIdProvider.scale(anyInt())).thenReturn(CompletableFuture.completedFuture(null));
      final var executor =
          new ClusterChangeExecutorImpl(
              new TestConcurrencyControl(), nodeIdProvider, Optional.empty());

      // when
      final var result =
          executor.postScaling(Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // then
      Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
      verify(nodeIdProvider, times(1)).scale(3);
    }

    @Test
    void shouldCompleteExceptionallyWhenNodeIdProviderScaleFails() {
      // given
      final var nodeIdProvider = mock(NodeIdProvider.class);
      when(nodeIdProvider.scale(anyInt()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("scale failed")));
      final var executor =
          new ClusterChangeExecutorImpl(
              new TestConcurrencyControl(), nodeIdProvider, Optional.empty());

      // when
      final var result =
          executor.postScaling(Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // then
      Assertions.assertThat(result)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining("scale failed");
      verify(nodeIdProvider, times(1)).scale(3);
    }
  }
}
