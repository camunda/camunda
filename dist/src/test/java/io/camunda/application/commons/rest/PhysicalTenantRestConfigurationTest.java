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
import static org.mockito.Mockito.when;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.JobMetricsConfig;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider.JobMetrics;
import java.time.Duration;
import java.util.Map;
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
  @Mock private GatewayRestConfiguration gatewayRestConfiguration;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Camunda tenantA;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Camunda tenantB;

  @Mock private JobMetricsConfig jobMetricsConfigA;
  @Mock private JobMetricsConfig jobMetricsConfigB;

  @BeforeEach
  void setUp() {
    when(gatewayRestConfiguration.getMaxNameFieldLength()).thenReturn(8192);
    stubJobMetrics(tenantA, jobMetricsConfigA);
    stubJobMetrics(tenantB, jobMetricsConfigB);
    stubDefaultJobMetricsValues(jobMetricsConfigA);
    stubDefaultJobMetricsValues(jobMetricsConfigB);
  }

  @Test
  void shouldUseGlobalMaxNameFieldLengthForNonRdbmsTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    when(physicalTenantResolver.getAll()).thenReturn(Map.of("tenant-es", tenantA));

    // when
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // then
    assertThat(provider.forTenant("tenant-es").maxNameFieldLength()).isEqualTo(8192);
  }

  @Test
  void shouldUseRdbmsMaxVarcharLengthForRdbmsTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType()).thenReturn(SecondaryStorageType.rdbms);
    when(tenantA.getData().getSecondaryStorage().getRdbms().getMaxVarcharFieldLength())
        .thenReturn(4000);
    when(physicalTenantResolver.getAll()).thenReturn(Map.of("tenant-rdbms", tenantA));

    // when
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // then
    assertThat(provider.forTenant("tenant-rdbms").maxNameFieldLength()).isEqualTo(4000);
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
    when(physicalTenantResolver.getAll()).thenReturn(Map.of("tenant-1", tenantA));

    // when
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // then
    assertThat(provider.forTenant("tenant-1").jobMetrics())
        .isEqualTo(new JobMetrics(false, Duration.ofMinutes(10), 50, 200, 15, 5000));
  }

  @Test
  void shouldThrowForUnknownTenant() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    when(physicalTenantResolver.getAll()).thenReturn(Map.of("known-tenant", tenantA));
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // when / then
    assertThatThrownBy(() -> provider.forTenant("unknown-tenant"))
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
    when(physicalTenantResolver.getAll())
        .thenReturn(Map.of("es-tenant", tenantA, "rdbms-tenant", tenantB));

    // when
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // then
    assertThat(provider.forTenant("es-tenant").maxNameFieldLength()).isEqualTo(8192);
    assertThat(provider.forTenant("rdbms-tenant").maxNameFieldLength()).isEqualTo(4000);
  }

  @Test
  void shouldReturnConfigForExactlyRegisteredTenants() {
    // given
    when(tenantA.getData().getSecondaryStorage().getType())
        .thenReturn(SecondaryStorageType.elasticsearch);
    when(physicalTenantResolver.getAll()).thenReturn(Map.of("tenant-a", tenantA));
    final var provider =
        configuration.physicalTenantRestConfigProvider(
            physicalTenantResolver, gatewayRestConfiguration);

    // when / then
    assertThat(provider.forTenant("tenant-a")).isNotNull();
    assertThatThrownBy(() -> provider.forTenant("tenant-b"))
        .isInstanceOf(IllegalArgumentException.class);
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
