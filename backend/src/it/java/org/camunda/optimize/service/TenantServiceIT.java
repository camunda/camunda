/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.service.util.configuration.DefaultTenant;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TenantServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getStoredTenants() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";

    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, "engine"));

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenants();

    // then
    assertThat(tenants.size(), is(2));
    assertThat(tenants.get(0), is(TenantService.TENANT_NONE));
    assertThat(tenants.get(1).getId(), is(tenantId));
    assertThat(tenants.get(1).getName(), is(tenantName));
  }

  @Test
  public void getDefaultTenants() {
    // given
    final String tenantId = "myTenantId";
    final String tenantName = "Default Tenant";
    setDefaultTenant(new DefaultTenant(tenantId, tenantName));
    embeddedOptimizeRule.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenants();

    // then
    assertThat(tenants.size(), is(2));
    assertThat(tenants.get(0), is(TenantService.TENANT_NONE));
    assertThat(tenants.get(1).getId(), is(tenantId));
    assertThat(tenants.get(1).getName(), is(tenantName));
  }

  @Test
  public void getDefaultTenantsNoCustomNameDefaultsToId() {
    // given
    final String tenantId = "myTenantId";
    setDefaultTenant(new DefaultTenant(tenantId, null));
    embeddedOptimizeRule.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenants();

    // then
    assertThat(tenants.size(), is(2));
    assertThat(tenants.get(0), is(TenantService.TENANT_NONE));
    assertThat(tenants.get(1).getId(), is(tenantId));
    assertThat(tenants.get(1).getName(), is(tenantId));
  }

  @Test
  public void getStoredAndDefaultTenants() {
    // given
    final String storedTenantId = "1";
    final String storedTenantName = "My Tenant";
    final String defaultTenantId = "myTenantId";
    final String defaultTenantName = "Default Tenant";

    addTenantToElasticsearch(new TenantDto(storedTenantId, storedTenantName, "engine"));
    setDefaultTenant(new DefaultTenant(defaultTenantId, defaultTenantName));
    embeddedOptimizeRule.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenants();

    // then
    assertThat(tenants.size(), is(3));
    assertThat(tenants.get(0), is(TenantService.TENANT_NONE));
    assertThat(tenants.get(1).getId(), is(defaultTenantId));
    assertThat(tenants.get(1).getName(), is(defaultTenantName));
    assertThat(tenants.get(2).getId(), is(storedTenantId));
    assertThat(tenants.get(2).getName(), is(storedTenantName));
  }

  private void setDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeRule.getConfigurationService().getConfiguredEngines().values().iterator().next().setDefaultTenant(defaultTenant);
  }

  private void addTenantToElasticsearch(final TenantDto engine) {
    elasticSearchRule.addEntryToElasticsearch(ElasticsearchConstants.TENANT_TYPE, engine.getId(), engine);
  }

}
