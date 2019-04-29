/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.service.es.reader.TenantReader;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TenantService implements ConfigurationReloadable {

  private final TenantReader tenantReader;
  private final ConfigurationService configurationService;

  private List<TenantDto> configuredDefaultTenants;

  public TenantService(final TenantReader tenantReader, final ConfigurationService configurationService) {
    this.tenantReader = tenantReader;
    this.configurationService = configurationService;

    initDefaultTenants();
  }

  public List<TenantDto> getTenants() {
    final List<TenantDto> tenants = new ArrayList<>(configuredDefaultTenants);
    tenants.addAll(tenantReader.getTenants());
    return tenants;
  }

  private void initDefaultTenants() {
    this.configuredDefaultTenants = configurationService.getConfiguredEngines().entrySet()
      .stream()
      .filter(entry -> entry.getValue().getDefaultTenantId().isPresent())
      .map(entry -> {
        final String tenantId = entry.getValue().getDefaultTenantId().get();
        return new TenantDto(tenantId, entry.getValue().getDefaultTenantName().orElse(tenantId), entry.getKey());
      })
      .collect(ImmutableList.toImmutableList());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initDefaultTenants();
  }
}
