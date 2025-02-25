/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.tenant;

import static org.mockito.Mockito.*;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.tasklist.webapp.security.tenant.TenantServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

  @Spy private SecurityConfiguration securityConfiguration;
  @InjectMocks private TenantServiceImpl instance;

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOff() {
    RequestContextHolder.setRequestAttributes(null);
    Assertions.assertThat(instance.getAuthenticatedTenants())
        .isEqualTo(TenantService.AuthenticatedTenants.allTenants());
  }

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOn() {
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    securityConfiguration.getMultiTenancy().setEnabled(true);
    prepareMocksTenants();

    final List<String> expectedListOfTenants = new ArrayList<String>();
    expectedListOfTenants.add("A");
    expectedListOfTenants.add("B");

    final TenantService.AuthenticatedTenants result = instance.getAuthenticatedTenants();
    Assertions.assertThat(result.getTenantIds()).isEqualTo(expectedListOfTenants);
    Assertions.assertThat(result.getTenantAccessType())
        .isEqualTo(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
  }

  @Test
  void invalidTenant() {
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    final String tenantId = "C";
    securityConfiguration.getMultiTenancy().setEnabled(true);
    prepareMocksTenants();
    Assertions.assertThat(instance.isTenantValid(tenantId)).isFalse();
  }

  @Test
  void validTenant() {
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
    final String tenantId = "A";
    securityConfiguration.getMultiTenancy().setEnabled(true);
    prepareMocksTenants();
    Assertions.assertThat(instance.isTenantValid(tenantId)).isTrue();
  }

  private void prepareMocksTenants() {
    final List<String> listOfTenants = new ArrayList<>();
    listOfTenants.add("A");
    listOfTenants.add("B");
    when(instance.tenantsIds()).thenReturn(listOfTenants);
  }
}
