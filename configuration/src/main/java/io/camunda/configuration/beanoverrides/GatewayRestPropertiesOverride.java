/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Executor;
import io.camunda.configuration.JobMetricsConfig;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.configuration.beans.LegacyGatewayRestProperties;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ApiExecutorConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacyGatewayRestProperties.class)
@DependsOn("unifiedConfigurationHelper")
public class GatewayRestPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyGatewayRestProperties legacyGatewayRestProperties;

  public GatewayRestPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyGatewayRestProperties legacyGatewayRestProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyGatewayRestProperties = legacyGatewayRestProperties;
  }

  @Bean
  @Primary
  public GatewayRestProperties gatewayRestProperties() {
    final GatewayRestProperties override = new GatewayRestProperties();
    BeanUtils.copyProperties(legacyGatewayRestProperties, override);
    populateFromExecutor(override);
    new Converter(unifiedConfiguration.getCamunda()).applyTo(override);
    return override;
  }

  private void populateFromExecutor(final GatewayRestProperties override) {
    final Executor executor = unifiedConfiguration.getCamunda().getApi().getRest().getExecutor();
    final ApiExecutorConfiguration apiExecutorConfiguration = override.getApiExecutor();
    apiExecutorConfiguration.setCorePoolSizeMultiplier(executor.getCorePoolSizeMultiplier());
    apiExecutorConfiguration.setMaxPoolSizeMultiplier(executor.getMaxPoolSizeMultiplier());
    apiExecutorConfiguration.setKeepAliveSeconds(executor.getKeepAlive().getSeconds());
    apiExecutorConfiguration.setQueueCapacity(executor.getQueueCapacity());
  }

  /** Maps a {@link Camunda} configuration into a {@link GatewayRestConfiguration}. */
  public static final class Converter {

    private final Camunda camunda;

    public Converter(final Camunda camunda) {
      this.camunda = camunda;
    }

    public GatewayRestConfiguration convert() {
      final GatewayRestConfiguration config = new GatewayRestConfiguration();
      applyTo(config);
      return config;
    }

    public void applyTo(final GatewayRestConfiguration override) {
      populateFromJobMetrics(override);
      populateFromValidators(override);
    }

    private void populateFromJobMetrics(final GatewayRestConfiguration override) {
      final JobMetricsConfig jobMetrics = camunda.getMonitoring().getMetrics().getJobMetrics();
      final JobMetricsConfiguration jobMetricsConfiguration = override.getJobMetrics();
      jobMetricsConfiguration.setExportInterval(jobMetrics.getExportInterval());
      jobMetricsConfiguration.setMaxWorkerNameLength(jobMetrics.getMaxWorkerNameLength());
      jobMetricsConfiguration.setMaxJobTypeLength(jobMetrics.getMaxJobTypeLength());
      jobMetricsConfiguration.setMaxTenantIdLength(jobMetrics.getMaxTenantIdLength());
      jobMetricsConfiguration.setMaxUniqueKeys(jobMetrics.getMaxUniqueKeys());
      jobMetricsConfiguration.setEnabled(jobMetrics.isEnabled());
    }

    private void populateFromValidators(final GatewayRestConfiguration override) {
      if (camunda.getData().getSecondaryStorage().getType() != SecondaryStorageType.rdbms) {
        return;
      }
      final var maxNameFieldLength =
          camunda.getData().getSecondaryStorage().getRdbms().getMaxVarcharFieldLength();
      override.setMaxNameFieldLength(maxNameFieldLength);
    }
  }
}
