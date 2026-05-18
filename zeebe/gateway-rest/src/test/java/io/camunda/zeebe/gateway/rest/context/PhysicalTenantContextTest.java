/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.context;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PhysicalTenantContextTest {

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldExposeStableConstants() {
    // then
    assertThat(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID).isEqualTo("default");
    assertThat(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID)
        .isEqualTo("physicalTenantId");
    assertThat(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID)
        .isEqualTo(PhysicalTenantContext.class.getName() + ".PHYSICAL_TENANT_ID");
  }

  @Test
  void shouldRoundTripValueViaRequestAttribute() {
    // given
    final HttpServletRequest request = new MockHttpServletRequest();

    // when
    PhysicalTenantContext.setPhysicalTenantId(request, "tenant-a");

    // then
    assertThat(request.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .isEqualTo("tenant-a");
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenant-a");
  }

  @Test
  void shouldReturnDefaultFromCurrentWhenNoRequestBound() {
    // given: no RequestContextHolder bound
    RequestContextHolder.resetRequestAttributes();

    // when / then
    assertThat(PhysicalTenantContext.current())
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldReturnBoundValueFromCurrent() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenant-b");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // when / then
    assertThat(PhysicalTenantContext.current()).isEqualTo("tenant-b");
  }

  @Test
  void shouldNotReturnDefaultFromGetWhenAttributeMissing() {
    // given
    final HttpServletRequest request = new MockHttpServletRequest();

    // when / then
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isNotEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotReturnDefaultFromGetWhenAttributeIsPresent() {
    // given
    final HttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, 42);

    // when / then
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isNotEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotReturnDefaultFromCurrentWhenBoundAttributeIsNotAString() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, 42);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // when / then
    assertThat(PhysicalTenantContext.current())
        .isNotEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
  }
}
