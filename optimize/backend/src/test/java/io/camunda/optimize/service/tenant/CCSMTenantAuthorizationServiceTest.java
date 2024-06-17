/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.tenant;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CCSMTenantAuthorizationServiceTest {

  private static final String TEST_TOKEN = "someAuthToken";
  private static final String TEST_USER_ID = "someUserId";

  @Mock private CCSMTokenService ccsmTokenService;
  @Mock private ConfigurationService configurationService;

  private CamundaCCSMTenantAuthorizationService underTest;

  @BeforeEach
  public void beforeEach() {
    when(configurationService.isMultiTenancyEnabled()).thenReturn(true);
    final CacheConfiguration tenantCacheConfig = new CacheConfiguration();
    tenantCacheConfig.setMaxSize(10);
    tenantCacheConfig.setDefaultTtlMillis(10000);
    final GlobalCacheConfiguration cacheConfig = new GlobalCacheConfiguration();
    cacheConfig.setCloudTenantAuthorizations(tenantCacheConfig);
    when(configurationService.getCaches()).thenReturn(cacheConfig);
    when(ccsmTokenService.getCurrentUserIdFromAuthToken()).thenReturn(Optional.of(TEST_USER_ID));
    when(ccsmTokenService.getCurrentUserAuthToken()).thenReturn(Optional.of(TEST_TOKEN));
    underTest = new CamundaCCSMTenantAuthorizationService(ccsmTokenService, configurationService);
  }

  @Test
  public void usersAuthorizedToDefaultTenantWhenMultiTenancyDisabled() {
    // given
    when(configurationService.isMultiTenancyEnabled()).thenReturn(false);

    // when
    final List<TenantDto> actualAuthorizedTenants = underTest.getCurrentUserAuthorizedTenants();

    // then
    Mockito.verify(ccsmTokenService, never()).getAuthorizedTenantsFromToken(any());
    assertThat(actualAuthorizedTenants).singleElement().isEqualTo(ZEEBE_DEFAULT_TENANT);
    assertTenantAuthorization(ZEEBE_DEFAULT_TENANT_ID);
    assertNoTenantAuthorization("unauthorizedTenantId");
    assertTenantAuthorization(List.of(ZEEBE_DEFAULT_TENANT_ID));
    assertNoTenantAuthorization(List.of("unauthorizedTenantId"));
    assertNoTenantAuthorization(List.of(ZEEBE_DEFAULT_TENANT_ID, "unauthorizedTenantId"));
  }

  @Test
  public void usersAuthorizedToTenantsRetrievedFromIdentity() {
    // given
    final List<TenantDto> authorizedTenants =
        List.of(
            new TenantDto("tenant1Id", "tenant1", ZEEBE_DATA_SOURCE),
            new TenantDto("tenant2Id", "tenant2", ZEEBE_DATA_SOURCE));
    when(ccsmTokenService.getAuthorizedTenantsFromToken(TEST_TOKEN)).thenReturn(authorizedTenants);

    // when
    final List<TenantDto> actualAuthorizedTenants = underTest.getCurrentUserAuthorizedTenants();

    // then
    Mockito.verify(ccsmTokenService, times(1)).getAuthorizedTenantsFromToken(TEST_TOKEN);
    assertThat(actualAuthorizedTenants).containsExactlyInAnyOrderElementsOf(authorizedTenants);
    assertTenantAuthorization("tenant1Id");
    assertNoTenantAuthorization("unauthorizedTenantId");
    assertTenantAuthorization(authorizedTenants.stream().map(TenantDto::getId).toList());
    assertNoTenantAuthorization(List.of("tenant3", "tenant4"));
    assertNoTenantAuthorization(List.of("tenant1Id", "tenant3", "tenant4"));
  }

  @Test
  public void tenantAuthorizationCacheIsUsed() {
    // given
    final List<TenantDto> authorizedTenants =
        List.of(
            new TenantDto("tenant1Id", "tenant1", ZEEBE_DATA_SOURCE),
            new TenantDto("tenant2Id", "tenant2", ZEEBE_DATA_SOURCE));
    when(ccsmTokenService.getAuthorizedTenantsFromToken(TEST_TOKEN)).thenReturn(authorizedTenants);

    // when
    List<TenantDto> actualAuthorizedTenants = underTest.getCurrentUserAuthorizedTenants();

    // then
    Mockito.verify(ccsmTokenService, times(1)).getAuthorizedTenantsFromToken(TEST_TOKEN);
    assertThat(actualAuthorizedTenants).containsExactlyInAnyOrderElementsOf(authorizedTenants);

    // when retrieving authorizations again before cache expires
    actualAuthorizedTenants = underTest.getCurrentUserAuthorizedTenants();

    // then authorized tenants are taken from cache, so ccsmTokenService is not called a second time
    // (just once overall)
    Mockito.verify(ccsmTokenService, times(1)).getAuthorizedTenantsFromToken(TEST_TOKEN);
    assertThat(actualAuthorizedTenants).containsExactlyInAnyOrderElementsOf(authorizedTenants);
  }

  @Test
  public void noTenantAuthorizationsReturnedIfNoUserTokenPresent() {
    // given
    when(ccsmTokenService.getCurrentUserIdFromAuthToken()).thenReturn(Optional.empty());

    // when
    final List<TenantDto> actualAuthorizedTenants = underTest.getCurrentUserAuthorizedTenants();

    // then
    Mockito.verify(ccsmTokenService, never()).getAuthorizedTenantsFromToken(any());
    assertThat(actualAuthorizedTenants).isEmpty();
  }

  private void assertTenantAuthorization(final String authorizedTenantId) {
    assertThat(
            underTest.isAuthorizedToSeeTenant(
                TEST_USER_ID, IdentityType.USER, authorizedTenantId, ZEEBE_DATA_SOURCE))
        .isTrue();
    assertThat(
            underTest.isAuthorizedToSeeTenant(TEST_USER_ID, IdentityType.USER, authorizedTenantId))
        .isTrue();
  }

  private void assertNoTenantAuthorization(final String unauthorizedTenantId) {
    assertThat(
            underTest.isAuthorizedToSeeTenant(
                TEST_USER_ID, IdentityType.USER, unauthorizedTenantId))
        .isFalse();
    assertThat(
            underTest.isAuthorizedToSeeTenant(
                TEST_USER_ID, IdentityType.USER, unauthorizedTenantId, ZEEBE_DATA_SOURCE))
        .isFalse();
  }

  private void assertTenantAuthorization(final List<String> authorizedTenantIds) {
    assertThat(
            underTest.isAuthorizedToSeeAllTenants(
                TEST_USER_ID, IdentityType.USER, authorizedTenantIds))
        .isTrue();
  }

  private void assertNoTenantAuthorization(final List<String> unauthorizedTenantIds) {
    assertThat(
            underTest.isAuthorizedToSeeAllTenants(
                TEST_USER_ID, IdentityType.USER, unauthorizedTenantIds))
        .isFalse();
  }
}
