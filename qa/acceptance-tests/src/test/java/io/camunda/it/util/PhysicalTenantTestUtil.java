/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;

public final class PhysicalTenantTestUtil {

  private PhysicalTenantTestUtil() {}

  /**
   * When the test runs under a physical tenant, assigns the cluster's default OIDC provider to that
   * tenant. A non-default physical tenant must declare {@code
   * security.authentication.providers.assigned} when authentication is OIDC (it does not inherit
   * from root; see {@code PhysicalTenantAssignedProvidersValidation}, #54730 / ADR-0004), otherwise
   * the application context fails to start. Outside a physical-tenant run this is a no-op. It only
   * wires the tenant to the existing provider and does not change what the test asserts.
   */
  public static <T extends TestSpringApplication<T>> T assignDefaultOidcProviderForPhysicalTenant(
      final T application) {
    final String physicalTenant = CamundaMultiDBExtension.getPhysicalTenant();
    if (physicalTenant != null) {
      application.withProperty(
          "camunda.physical-tenants."
              + physicalTenant
              + ".security.authentication.providers.assigned[0]",
          "oidc");
    }
    return application;
  }
}
