/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.tenant;

import static org.mockito.Mockito.*;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private TenantAccessProvider tenantAccessProvider;
  @Spy private SecurityConfiguration securityConfiguration;
  @InjectMocks private TenantService instance;

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOff() {
    RequestContextHolder.setRequestAttributes(null);
    Assertions.assertThat(instance.getAuthenticatedTenants().wildcard()).isTrue();
  }

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOn() {
    // given
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    // prepare list of tenant IDs
    final List<String> listOfTenants = new ArrayList<>();
    listOfTenants.add("A");
    listOfTenants.add("B");

    securityConfiguration.getMultiTenancy().setChecksEnabled(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(mock(CamundaAuthentication.class));
    when(tenantAccessProvider.resolveTenantAccess(any(CamundaAuthentication.class)))
        .thenReturn(TenantAccess.allowed(listOfTenants));

    final List<String> expectedListOfTenants = new ArrayList<String>();
    expectedListOfTenants.add("A");
    expectedListOfTenants.add("B");

    // when
    final var result = instance.getAuthenticatedTenants();

    // then
    Assertions.assertThat(result.tenantIds()).isEqualTo(expectedListOfTenants);
    Assertions.assertThat(result.allowed()).isTrue();
    Assertions.assertThat(result.wildcard()).isFalse();
  }

  @Test
  void invalidTenant() {
    // given
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    final String tenantId = "C";

    securityConfiguration.getMultiTenancy().setChecksEnabled(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(mock(CamundaAuthentication.class));
    when(tenantAccessProvider.hasTenantAccessByTenantId(
            any(CamundaAuthentication.class), eq(tenantId)))
        .thenReturn(TenantAccess.denied(List.of(tenantId)));

    // when / then
    Assertions.assertThat(instance.isTenantValid(tenantId)).isFalse();
  }

  @Test
  void validTenant() {
    // given
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    final String tenantId = "A";

    securityConfiguration.getMultiTenancy().setChecksEnabled(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(mock(CamundaAuthentication.class));
    when(tenantAccessProvider.hasTenantAccessByTenantId(
            any(CamundaAuthentication.class), eq(tenantId)))
        .thenReturn(TenantAccess.allowed(List.of(tenantId)));

    // when / then
    Assertions.assertThat(instance.isTenantValid(tenantId)).isTrue();
  }
}
