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
 * Verifies that {@link Gateway#applyMaxConnectionAge(NettyServerBuilder, NetworkCfg)} only
 * configures grpc-java's server-side {@code maxConnectionAge}/{@code maxConnectionAgeGrace} when
 * explicitly enabled, leaving grpc-java's own defaults untouched otherwise.
 */
final class GatewayNetworkConfigTest {

  @Test
  void shouldNotConfigureMaxConnectionAgeWhenDisabledByDefault() {
    // given
    final var cfg = new NetworkCfg();
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verifyNoInteractions(serverBuilder);
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
  void shouldConfigureMaxConnectionAgeWithoutGraceWhenGraceNotSet() {
    // given
    final var maxConnectionAge = Duration.ofMinutes(30);
    final var cfg = new NetworkCfg().setMaxConnectionAge(maxConnectionAge);
    final var serverBuilder = mock(NettyServerBuilder.class);

    // when
    Gateway.applyMaxConnectionAge(serverBuilder, cfg);

    // then
    verify(serverBuilder).maxConnectionAge(maxConnectionAge.toMillis(), TimeUnit.MILLISECONDS);
  }
}
