/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.spring.utils.PhysicalTenantCpuCharger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class PhysicalTenantCpuChargingFilterTest {

  private final List<String> chargedTenants = new ArrayList<>();
  private final PhysicalTenantCpuChargingFilter filter =
      new PhysicalTenantCpuChargingFilter(
          new PhysicalTenantCpuCharger((tenant, nanos) -> chargedTenants.add(tenant)));

  @Test
  void shouldChargeRequestToTenantStampedByTheChain() throws Exception {
    // given - the tenant is only known once the chain has run, like with PhysicalTenantFilter
    final var request = new MockHttpServletRequest();
    final var chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              final jakarta.servlet.ServletRequest req, final jakarta.servlet.ServletResponse res) {
            PhysicalTenantContext.setPhysicalTenantId(request, "blue");
          }
        };

    // when
    filter.doFilter(request, new MockHttpServletResponse(), chain);

    // then
    assertThat(chargedTenants).containsExactly("blue");
  }

  @Test
  void shouldChargeRequestWithoutTenantPrefixToDefaultTenant() throws Exception {
    // when
    filter.doFilter(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

    // then
    assertThat(chargedTenants).containsExactly(DEFAULT_PHYSICAL_TENANT_ID);
  }
}
