/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.spring.utils.PhysicalTenantCpuCharger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Servlet {@link Filter} that charges the CPU time of the request thread to the physical tenant of
 * the request. It never delays or rejects a request.
 *
 * <p>The filter must run before {@link PhysicalTenantFilter}, so that it also covers the security
 * chain. It reads the tenant after the chain has run, when {@link PhysicalTenantFilter} has stamped
 * it onto the request. A request without a tenant prefix belongs to the default tenant.
 *
 * <p>A request that suspends for asynchronous handling is dispatched again when its result is
 * ready. The filter must be registered for both dispatches, so that it also charges the thread that
 * writes the response. The threads of the API executor charge their own work.
 */
public final class PhysicalTenantCpuChargingFilter implements Filter {

  private final PhysicalTenantCpuCharger cpuCharger;

  public PhysicalTenantCpuChargingFilter(final PhysicalTenantCpuCharger cpuCharger) {
    this.cpuCharger = cpuCharger;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final var cpuScope = cpuCharger.open();
    try {
      chain.doFilter(request, response);
    } finally {
      cpuScope.chargeTo(physicalTenantOf(request));
    }
  }

  private static String physicalTenantOf(final ServletRequest request) {
    final var physicalTenantId =
        request instanceof final HttpServletRequest http
            ? PhysicalTenantContext.getPhysicalTenantId(http)
            : null;
    return physicalTenantId != null ? physicalTenantId : DEFAULT_PHYSICAL_TENANT_ID;
  }
}
