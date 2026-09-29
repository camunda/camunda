/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PartitionId;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.LeaderWarmupCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.warmup.LeaderWarmupMetrics.LeaderWarmupMetricsDoc;
import io.camunda.zeebe.broker.warmup.LeaderWarmupMetrics.State;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class LeaderWarmupTest {

  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  @TempDir private Path dataDirectory;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final BrokerHealthCheckService healthCheckService = mock(BrokerHealthCheckService.class);
  private final LeaderWarmupCfg cfg = new LeaderWarmupCfg();
  private LeaderWarmup warmup;
  private volatile double processCpuLoad;

  @BeforeEach
  void setUp() {
    cfg.setStartDelay(Duration.ZERO);
    cfg.setQuietPeriod(Duration.ZERO);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (warmup != null) {
      warmup.closeAsync().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }
  }

  @ParameterizedTest
  @MethodSource("securityConfigs")
  void shouldRunToCompletionAndRemoveItsDirectory(final EngineSecurityConfig securityConfig)
      throws Exception {
    // given
    cfg.setProcessInstances(200);
    warmup = createWarmup(securityConfig);

    // when
    warmup.start();

    // then
    await().atMost(TIMEOUT).until(this::state, s -> s == State.COMPLETED);
    warmup.closeAsync().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    assertThat(warmupDirectory()).doesNotExist();
    assertThat(meterRegistry.get(LeaderWarmupMetricsDoc.DURATION.getName()).timeGauge().value())
        .isPositive();
  }

  @Test
  void shouldSkipIfAlreadyLeader() throws Exception {
    // given
    warmup = createWarmup(EngineSecurityConfigurations.defaultConfig());
    warmup.onBecameRaftLeader(new PartitionId("default", 1), 1);

    // when
    warmup.start();

    // then
    await().atMost(TIMEOUT).until(this::state, s -> s == State.SKIPPED);
    assertThat(warmupDirectory()).doesNotExist();
  }

  @Test
  void shouldNotStartWhileBrokerIsUnhealthy() {
    // given
    warmup = createWarmup(EngineSecurityConfigurations.defaultConfig());
    when(healthCheckService.isBrokerHealthy()).thenReturn(false);

    // when
    warmup.start();

    // then
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .until(this::state, s -> s == State.PENDING);
    assertThat(warmupDirectory()).doesNotExist();
  }

  @Test
  void shouldWaitForTheBrokerToBeQuietBeforeStarting() {
    // given
    cfg.setQuietPeriod(Duration.ofSeconds(2));
    processCpuLoad = 0.9;
    warmup = createWarmup(EngineSecurityConfigurations.defaultConfig());
    warmup.start();
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(5))
        .until(this::state, s -> s == State.PENDING);

    // when
    processCpuLoad = 0.2;

    // then
    await().atMost(TIMEOUT).until(this::state, s -> s != State.PENDING);
    assertThat(state()).isIn(State.RUNNING, State.COMPLETED);
  }

  @Test
  void shouldCancelWhenBecomingLeader() throws Exception {
    // given
    cfg.setProcessInstances(Integer.MAX_VALUE);
    warmup = createWarmup(EngineSecurityConfigurations.defaultConfig());
    warmup.start();
    await().atMost(TIMEOUT).until(this::state, s -> s == State.RUNNING);

    // when
    warmup.onBecameRaftLeader(new PartitionId("default", 1), 2);

    // then
    await().atMost(TIMEOUT).until(this::state, s -> s == State.CANCELLED);
    warmup.closeAsync().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    assertThat(warmupDirectory()).doesNotExist();
  }

  @Test
  void shouldEndQuietlyOnFailure() throws Exception {
    // given
    final var blockingFile = dataDirectory.resolve("not-a-directory");
    Files.writeString(blockingFile, "");
    final var brokerCfg = new BrokerCfg();
    brokerCfg.init(blockingFile.resolve("data").toString());
    warmup = createWarmup(EngineSecurityConfigurations.defaultConfig(), brokerCfg);

    // when
    warmup.start();

    // then
    await().atMost(TIMEOUT).until(this::state, s -> s == State.FAILED);
    assertThat(warmup.closeAsync()).succeedsWithin(TIMEOUT);
  }

  private static Stream<EngineSecurityConfig> securityConfigs() {
    return Stream.of(
        EngineSecurityConfigurations.defaultConfig(),
        EngineSecurityConfigurations.unauthenticatedAndUnauthorized());
  }

  private LeaderWarmup createWarmup(final EngineSecurityConfig securityConfig) {
    return createWarmup(securityConfig, brokerCfg());
  }

  private LeaderWarmup createWarmup(
      final EngineSecurityConfig securityConfig, final BrokerCfg brokerCfg) {
    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    final var tenantContext =
        new PhysicalTenantContext(
            securityConfig,
            new BrokerRequestAuthorizationConverter(securityConfig),
            FeatureFlags.createDefault(),
            brokerCfg,
            new ExporterRepository(),
            new SecretStoreRegistry(Map.of()));
    return new LeaderWarmup(
        cfg, brokerCfg, tenantContext, healthCheckService, meterRegistry, () -> processCpuLoad);
  }

  private BrokerCfg brokerCfg() {
    final var brokerCfg = new BrokerCfg();
    brokerCfg.init(dataDirectory.toString());
    return brokerCfg;
  }

  private Path warmupDirectory() {
    return dataDirectory.resolve("data").resolve("leader-warmup");
  }

  private State state() {
    final var code =
        (int) meterRegistry.get(LeaderWarmupMetricsDoc.STATE.getName()).gauge().value();
    return Stream.of(State.values()).filter(s -> s.code() == code).findFirst().orElseThrow();
  }
}
