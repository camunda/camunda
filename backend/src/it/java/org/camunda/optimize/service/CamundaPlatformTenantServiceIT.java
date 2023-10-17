/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.tenant.CamundaPlatformTenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

public class CamundaPlatformTenantServiceIT extends AbstractPlatformIT {

  @Test
  public void getStoredTenants() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";

    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getPlatformTenantService().getAvailableTenants();

    // then
    assertThat(tenants).hasSize(2);
    assertThat(tenants.get(0)).isEqualTo(TENANT_NOT_DEFINED);
    assertThat(tenants.get(1).getId()).isEqualTo(tenantId);
    assertThat(tenants.get(1).getName()).isEqualTo(tenantName);
  }

  @Test
  public void getDefaultTenants() {
    // given
    final String tenantId = "myTenantId";
    final String tenantName = "Default Tenant";
    setDefaultTenant(new DefaultTenant(tenantId, tenantName));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getPlatformTenantService().getAvailableTenants();

    // then
    assertThat(tenants).hasSize(2);
    assertThat(tenants.get(0)).isEqualTo(TENANT_NOT_DEFINED);
    assertThat(tenants.get(1).getId()).isEqualTo(tenantId);
    assertThat(tenants.get(1).getName()).isEqualTo(tenantName);
  }

  @Test
  public void getDefaultTenantsNoCustomNameDefaultsToId() {
    // given
    final String tenantId = "myTenantId";
    setDefaultTenant(new DefaultTenant(tenantId));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getPlatformTenantService().getAvailableTenants();

    // then
    assertThat(tenants).hasSize(2);
    assertThat(tenants.get(0)).isEqualTo(TENANT_NOT_DEFINED);
    assertThat(tenants.get(1).getId()).isEqualTo(tenantId);
    assertThat(tenants.get(1).getName()).isEqualTo(tenantId);
  }

  @Test
  public void getStoredAndDefaultTenants() {
    // given
    final String storedTenantId = "1";
    final String storedTenantName = "My Tenant";
    final String defaultTenantId = "myTenantId";
    final String defaultTenantName = "Default Tenant";

    addTenantToElasticsearch(new TenantDto(storedTenantId, storedTenantName, DEFAULT_ENGINE_ALIAS));
    setDefaultTenant(new DefaultTenant(defaultTenantId, defaultTenantName));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getPlatformTenantService().getAvailableTenants();

    // then
    assertThat(tenants).hasSize(3);
    assertThat(tenants.get(0)).isEqualTo(TENANT_NOT_DEFINED);
    assertThat(tenants.get(1).getId()).isEqualTo(defaultTenantId);
    assertThat(tenants.get(1).getName()).isEqualTo(defaultTenantName);
    assertThat(tenants.get(2).getId()).isEqualTo(storedTenantId);
    assertThat(tenants.get(2).getName()).isEqualTo(storedTenantName);
  }

  @Test
  public void isAuthorizedToAccessTenant_noTenantAccess() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";
    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));
    final String tenantUser = "tenantUser";

    createOptimizeUser(tenantUser);

    // when
    final boolean isAuthorized = embeddedOptimizeExtension.getPlatformTenantService()
      .isAuthorizedToSeeTenant(tenantUser, tenantId);

    // then
    assertThat(isAuthorized).isFalse();
  }

  @Test
  public void isAuthorizedToAccessTenant_allTenantAccessGranted() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";
    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));
    final String tenantUser = "tenantUser";

    createUserWithTenantAuthorization(tenantUser, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID);

    // when
    final boolean isAuthorized = embeddedOptimizeExtension.getPlatformTenantService()
      .isAuthorizedToSeeTenant(tenantUser, tenantId);

    // then
    assertThat(isAuthorized).isTrue();
  }

  @Test
  public void isAuthorizedToAccessTenant_specificResourceAccessGranted() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";
    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));
    final String tenantUser = "tenantUser";

    createUserWithTenantAuthorization(tenantUser, ImmutableList.of(ALL_PERMISSION), tenantId);

    // when
    final boolean isAuthorized = embeddedOptimizeExtension.getPlatformTenantService()
      .isAuthorizedToSeeTenant(tenantUser, tenantId);

    // then
    assertThat(isAuthorized).isTrue();
  }

  @Test
  public void isAuthorizedToAccessTenant_specificResourceAccessRevoked() {
    // given
    final String tenantId = "1";
    final String tenantName = "My Tenant";
    addTenantToElasticsearch(new TenantDto(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));
    final String tenantUser = "tenantUser";

    createUserWithTenantAuthorization(tenantUser, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID);
    createTenantAuthorization(tenantUser, ImmutableList.of(ALL_PERMISSION), tenantId, AUTHORIZATION_TYPE_REVOKE);

    // when
    final boolean isAuthorized = embeddedOptimizeExtension.getPlatformTenantService()
      .isAuthorizedToSeeTenant(tenantUser, tenantId);

    // then
    assertThat(isAuthorized).isFalse();
  }

  private void createUserWithTenantAuthorization(final String tenantUser,
                                                 final ImmutableList<String> permissions,
                                                 final String resourceId) {
    createOptimizeUser(tenantUser);
    createTenantAuthorization(tenantUser, permissions, resourceId, AUTHORIZATION_TYPE_GRANT);
  }

  private void createTenantAuthorization(final String tenantUser, final ImmutableList<String> permissions,
                                         final String resourceId, int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(type);
    authorizationDto.setUserId(tenantUser);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void createOptimizeUser(final String tenantUser) {
    engineIntegrationExtension.addUser(tenantUser, tenantUser);
    engineIntegrationExtension.grantUserOptimizeAccess(tenantUser);
  }

  private void setDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeExtension.getConfigurationService()
      .getConfiguredEngines()
      .values()
      .iterator()
      .next()
      .setDefaultTenant(defaultTenant);
  }

  private void addTenantToElasticsearch(final TenantDto engine) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, engine.getId(), engine);
  }

}
