/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.resolver;

import static io.camunda.spring.utils.PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.utils.PhysicalTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Unit tests for {@link PhysicalTenantIdArgumentResolver}. */
class PhysicalTenantIdArgumentResolverTest {

  private final PhysicalTenantIdArgumentResolver resolver = new PhysicalTenantIdArgumentResolver();

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void resolvesStampedTenantIdForPrefixedPath() {
    // given a request stamped by PhysicalTenantFilter
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // when / then
    assertThat(resolver.resolveArgument(null, null, null, null)).isEqualTo("tenanta");
  }

  @Test
  void defaultsForClusterPathWithoutStampedId() {
    // given a cluster (non-prefixed) request — no PT id is stamped
    // when / then: must default, never null (a null id breaks command building downstream)
    assertThat(resolver.resolveArgument(null, null, null, null))
        .isEqualTo(DEFAULT_PHYSICAL_TENANT_ID);
  }
}
