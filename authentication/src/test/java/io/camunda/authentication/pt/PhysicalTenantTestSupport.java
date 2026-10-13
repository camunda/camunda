/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Helpers for tests that need a physical tenant bound to the thread or the root configuration. */
public final class PhysicalTenantTestSupport {

  private PhysicalTenantTestSupport() {}

  /**
   * Binds {@code tenantId} to the current thread as the physical tenant filter does for a request.
   * Callers reset it with {@link RequestContextHolder#resetRequestAttributes()}.
   */
  public static void bindPhysicalTenant(final String tenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  /**
   * The root security properties as the application has them: replaced by the default tenant's
   * resolved configuration whenever any physical tenant is configured. The environment is not bound
   * into the properties here, so configure at least one physical tenant.
   */
  public static CamundaSecurityLibraryProperties unifiedSecurityProperties(
      final Environment environment) {
    final var properties = new CamundaSecurityLibraryProperties();
    PhysicalTenantSecurityConfiguration.physicalTenantClusterAuthUnification(environment)
        .postProcessAfterInitialization(properties, "camundaSecurityLibraryProperties");
    return properties;
  }
}
