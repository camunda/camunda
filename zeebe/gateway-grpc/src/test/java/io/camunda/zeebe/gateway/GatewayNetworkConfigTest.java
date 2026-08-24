/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.grpc.netty.NettyServerBuilder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link Gateway#applyMaxConnectionAge(NettyServerBuilder, NetworkCfg)} configures
 * grpc-java's server-side {@code maxConnectionAge}/{@code maxConnectionAgeGrace} using {@link
 * NetworkCfg}'s defaults, and only skips doing so when explicitly disabled via {@code null} or a
 * non-positive duration, leaving grpc-java's own defaults untouched in that case.
 */
final class GatewayNetworkConfigTest {

  @Test
  void shouldConfigureMaxConnectionAgeAndGraceByDefault() {
    // given
    final var cfg = new NetworkCfg();
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verify(serverBuilder)
        .maxConnectionAge(cfg.getMaxConnectionAge().toMillis(), TimeUnit.MILLISECONDS);
    verify(serverBuilder)
        .maxConnectionAgeGrace(cfg.getMaxConnectionAgeGrace().toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldNotConfigureMaxConnectionAgeWhenExplicitlyZero() {
    // given
    final var cfg =
        new NetworkCfg().setMaxConnectionAge(Duration.ZERO).setMaxConnectionAgeGrace(Duration.ZERO);
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verifyNoInteractions(serverBuilder);
  }

  @Test
  void shouldNotConfigureMaxConnectionAgeWhenExplicitlyNull() {
    // given
    final var cfg = new NetworkCfg().setMaxConnectionAge(null).setMaxConnectionAgeGrace(null);
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verifyNoInteractions(serverBuilder);
  }

  @Test
  void shouldConfigureMaxConnectionAgeAndGraceWhenSet() {
    // given
    final var maxConnectionAge = Duration.ofMinutes(30);
    final var maxConnectionAgeGrace = Duration.ofSeconds(30);
    final var cfg =
        new NetworkCfg()
            .setMaxConnectionAge(maxConnectionAge)
            .setMaxConnectionAgeGrace(maxConnectionAgeGrace);
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verify(serverBuilder).maxConnectionAge(maxConnectionAge.toMillis(), TimeUnit.MILLISECONDS);
    verify(serverBuilder)
        .maxConnectionAgeGrace(maxConnectionAgeGrace.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldConfigureMaxConnectionAgeWithoutGraceWhenGraceExplicitlyNull() {
    // given
    final var maxConnectionAge = Duration.ofMinutes(30);
    final var cfg =
        new NetworkCfg().setMaxConnectionAge(maxConnectionAge).setMaxConnectionAgeGrace(null);
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verify(serverBuilder).maxConnectionAge(maxConnectionAge.toMillis(), TimeUnit.MILLISECONDS);
  }
}
