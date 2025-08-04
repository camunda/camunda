/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Filter;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.configuration.beans.LegacyGatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(LegacyGatewayBasedProperties.class)
@Profile("!broker")
@DependsOn("unifiedConfigurationHelper")
public class GatewayBasedPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyGatewayBasedProperties legacyGatewayBasedProperties;

  public GatewayBasedPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyGatewayBasedProperties legacyGatewayBasedProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyGatewayBasedProperties = legacyGatewayBasedProperties;
  }

  @Bean
  @Primary
  public GatewayBasedProperties gatewayBasedProperties() {
    final GatewayBasedProperties override = new GatewayBasedProperties();
    BeanUtils.copyProperties(legacyGatewayBasedProperties, override);

    populateFromLongPolling(override);
    populateFromRestFilters(override);

    return override;
  }

  private void populateFromRestFilters(final GatewayBasedProperties override) {
    final List<Filter> filters = unifiedConfiguration.getCamunda().getApi().getRest().getFilters();

    final List<FilterCfg> filterCfgList =
        filters.isEmpty()
            ? populateFromLegacyFilters(override.getFilters())
            : populateFromFilters(filters);

    override.setFilters(filterCfgList);
  }

  private List<FilterCfg> populateFromFilters(final List<Filter> filters) {
    return IntStream.range(0, filters.size())
        .mapToObj(
            i -> {
              final Filter filter = filters.get(i);
              return toFilterCfg(filter, i);
            })
        .toList();
  }

  private FilterCfg toFilterCfg(final Filter filter, final int idx) {
    final var filterCfg = new FilterCfg();
    filterCfg.setId(filter.getId(idx));
    filterCfg.setJarPath(filter.getJarPath(idx));
    filterCfg.setClassName(filter.getClassName(idx));
    return filterCfg;
  }

  private List<FilterCfg> populateFromLegacyFilters(final List<FilterCfg> legacyFilters) {
    return IntStream.range(0, legacyFilters.size())
        .mapToObj(
            i -> {
              final FilterCfg filterCfg = legacyFilters.get(i);
              patchFilterCfg(filterCfg, i);
              return filterCfg;
            })
        .toList();
  }

  private void patchFilterCfg(final FilterCfg filterCfg, final int index) {
    final var filter = new Filter();
    filter.setId(filterCfg.getId());
    filter.setJarPath(filterCfg.getJarPath());
    filter.setClassName(filterCfg.getClassName());

    filterCfg.setId(filter.getId(index));
    filterCfg.setJarPath(filter.getJarPath(index));
    filterCfg.setClassName(filter.getClassName(index));
  }

  private void populateFromLongPolling(final GatewayBasedProperties override) {
    final var longPolling = unifiedConfiguration.getCamunda().getApi().getLongPolling();
    final var longPollingCfg = override.getLongPolling();
    longPollingCfg.setEnabled(longPolling.isEnabled());
    longPollingCfg.setTimeout(longPolling.getTimeout());
    longPollingCfg.setProbeTimeout(longPolling.getProbeTimeout());
    longPollingCfg.setMinEmptyResponses(longPolling.getMinEmptyResponses());
  }
}
