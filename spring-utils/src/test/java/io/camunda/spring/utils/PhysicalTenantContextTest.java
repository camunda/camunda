/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PhysicalTenantContextTest {

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
    // guarantee the propagation ThreadLocal never bleeds into a later test, even on failure
    PhysicalTenantContext.clearPropagatedPhysicalTenant();
  }

  private void bindRequestWithPhysicalTenant(final String physicalTenantId) {
    final HttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void shouldExposeStableConstants() {
    // then
    assertThat(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID).isEqualTo("default");
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
  void shouldThrowWhenNoRequestBound() {
    // given: no RequestContextHolder bound
    RequestContextHolder.resetRequestAttributes();

    // when / then
    assertThatThrownBy(PhysicalTenantContext::current)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("request scope");
  }

  @Test
  void shouldReturnDefaultFromCurrentWhenRequestBoundButNoPtAttribute() {
    // given: request bound but no PT attribute set (non-prefixed /v2/... cluster request)
    final MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // when / then
    assertThat(PhysicalTenantContext.current())
        .isEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
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
        .isNotEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotReturnDefaultFromGetWhenAttributeIsPresent() {
    // given
    final HttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, 42);

    // when / then
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isNotEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotReturnDefaultFromCurrentWhenBoundAttributeIsNotAString() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, 42);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // when / then
    assertThat(PhysicalTenantContext.current())
        .isNotEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldReturnSupplierUnchangedWhenNoTenantInEffect() {
    // given no request scope and no propagated tenant bound (nothing to capture)
    final Supplier<String> original = () -> "result";

    // when
    final Supplier<String> wrapped = PhysicalTenantContext.propagateCurrent(original);

    // then the supplier is returned unchanged and nothing is propagated when it runs
    assertThat(wrapped).isSameAs(original);
    assertThat(wrapped.get()).isEqualTo("result");
    assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isNull();
  }

  @Test
  void shouldBindCapturedTenantAndClearWhenNoPriorPropagatedValue() {
    // given a request scope bound at capture time, but no prior propagated tenant
    bindRequestWithPhysicalTenant("tenant-a");
    final Supplier<String> wrapped =
        PhysicalTenantContext.propagateCurrent(PhysicalTenantContext::getPropagatedPhysicalTenant);

    // simulate a deferred run after the request scope is gone (e.g. session serialisation)
    RequestContextHolder.resetRequestAttributes();

    // when
    final String observedDuringRun = wrapped.get();

    // then the captured tenant was propagated during the run and cleared afterwards
    assertThat(observedDuringRun).isEqualTo("tenant-a");
    assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isNull();
  }

  @Test
  void shouldRestorePriorPropagatedValueAfterRun() {
    // given an already-propagated tenant on the current thread
    PhysicalTenantContext.setPropagatedPhysicalTenant("outer");
    // and a request scope bound at capture time so a different tenant is captured
    bindRequestWithPhysicalTenant("inner");
    final Supplier<String> wrapped =
        PhysicalTenantContext.propagateCurrent(PhysicalTenantContext::getPropagatedPhysicalTenant);

    // simulate a deferred run after the request scope is gone
    RequestContextHolder.resetRequestAttributes();

    // when
    final String observedDuringRun = wrapped.get();

    // then the captured tenant was propagated during the run and the prior value restored after
    assertThat(observedDuringRun).isEqualTo("inner");
    assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isEqualTo("outer");
  }
}
