/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.JobMetricsConfig;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhysicalTenantRestConfigurationTest {

  private final PhysicalTenantRestConfiguration configuration =
      new PhysicalTenantRestConfiguration();

  @Mock private PhysicalTenantResolver physicalTenantResolver;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Camunda tenantA;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Camunda tenantB;

  @Mock private JobMetricsConfig jobMetricsConfigA;
  @Mock private JobMetricsConfig jobMetricsConfigB;

  @BeforeEach
  void setUp() {
    stubJobMetrics(tenantA, jobMetricsConfigA);
    stubJobMetrics(tenantB, jobMetricsConfigB);
    stubDefaultJobMetricsValues(jobMetricsConfigA);
    stubDefaultJobMetricsValues(jobMetricsConfigB);
  }

  @Test
  void shouldUseDefaultMaxNameFieldLengthForNonRdbmsTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    stubMapValues(Map.of("tenant-es", tenantA));

    // when
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // then
    assertThat(provider.forPhysicalTenant("tenant-es").getMaxNameFieldLength())
        .isEqualTo(32 * 1024);
  }

  @Test
  void shouldUseRdbmsMaxVarcharLengthForRdbmsTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType()).thenReturn(SecondaryStorageType.rdbms);
    when(tenantA.getData().getSecondaryStorage().getRdbms().getMaxVarcharFieldLength())
        .thenReturn(4000);
    stubMapValues(Map.of("tenant-rdbms", tenantA));

    // when
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // then
    assertThat(provider.forPhysicalTenant("tenant-rdbms").getMaxNameFieldLength()).isEqualTo(4000);
  }

  @Test
  void shouldMapAllJobMetricsFieldsFromPerTenantConfig() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    when(jobMetricsConfigA.isEnabled()).thenReturn(false);
    when(jobMetricsConfigA.getExportInterval()).thenReturn(Duration.ofMinutes(10));
    when(jobMetricsConfigA.getMaxWorkerNameLength()).thenReturn(50);
    when(jobMetricsConfigA.getMaxJobTypeLength()).thenReturn(200);
    when(jobMetricsConfigA.getMaxTenantIdLength()).thenReturn(15);
    when(jobMetricsConfigA.getMaxUniqueKeys()).thenReturn(5000);
    stubMapValues(Map.of("tenant-1", tenantA));

    // when
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // then
    final var jm = provider.forPhysicalTenant("tenant-1").getJobMetrics();
    assertThat(jm.isEnabled()).isFalse();
    assertThat(jm.getExportInterval()).isEqualTo(Duration.ofMinutes(10));
    assertThat(jm.getMaxWorkerNameLength()).isEqualTo(50);
    assertThat(jm.getMaxJobTypeLength()).isEqualTo(200);
    assertThat(jm.getMaxTenantIdLength()).isEqualTo(15);
    assertThat(jm.getMaxUniqueKeys()).isEqualTo(5000);
  }

  @Test
  void shouldThrowForUnknownTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    stubMapValues(Map.of("known-tenant", tenantA));
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // when / then
    assertThatThrownBy(() -> provider.forPhysicalTenant("unknown-tenant"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-tenant");
  }

  @Test
  void shouldIsolateTenantConfigsForMultipleTenants() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    when(tenantB.getData().getSecondaryStorage().getType()).thenReturn(SecondaryStorageType.rdbms);
    when(tenantB.getData().getSecondaryStorage().getRdbms().getMaxVarcharFieldLength())
        .thenReturn(4000);
    stubMapValues(Map.of("es-tenant", tenantA, "rdbms-tenant", tenantB));

    // when
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // then
    assertThat(provider.forPhysicalTenant("es-tenant").getMaxNameFieldLength())
        .isEqualTo(32 * 1024);
    assertThat(provider.forPhysicalTenant("rdbms-tenant").getMaxNameFieldLength()).isEqualTo(4000);
  }

  @Test
  void shouldReturnConfigForExactlyRegisteredTenants() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    stubMapValues(Map.of("tenant-a", tenantA));
    final var provider = configuration.physicalTenantRestConfigProvider(physicalTenantResolver);

    // when / then
    assertThat(provider.forPhysicalTenant("tenant-a")).isNotNull();
    assertThatThrownBy(() -> provider.forPhysicalTenant("tenant-b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void stubMapValues(final Map<String, Camunda> tenants) {
    when(physicalTenantResolver.mapValues(any()))
        .thenAnswer(
            inv -> {
              final var mapper = (Function<Camunda, Object>) inv.getArgument(0);
              final var result = new java.util.LinkedHashMap<String, Object>();
              tenants.forEach((id, camunda) -> result.put(id, mapper.apply(camunda)));
              return result;
            });
  }

  private static void stubJobMetrics(final Camunda tenantConfig, final JobMetricsConfig config) {
    when(tenantConfig.getMonitoring().getMetrics().getJobMetrics()).thenReturn(config);
  }

  private static void stubDefaultJobMetricsValues(final JobMetricsConfig config) {
    when(config.isEnabled()).thenReturn(true);
    when(config.getExportInterval()).thenReturn(Duration.ofMinutes(5));
    when(config.getMaxWorkerNameLength()).thenReturn(100);
    when(config.getMaxJobTypeLength()).thenReturn(100);
    when(config.getMaxTenantIdLength()).thenReturn(30);
    when(config.getMaxUniqueKeys()).thenReturn(9500);
  }
}
