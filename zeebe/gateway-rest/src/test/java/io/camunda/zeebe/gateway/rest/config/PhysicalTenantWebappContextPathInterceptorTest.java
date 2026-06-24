/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.utils.PhysicalTenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

class PhysicalTenantWebappContextPathInterceptorTest {

  private final PhysicalTenantWebappContextPathInterceptor interceptor =
      new PhysicalTenantWebappContextPathInterceptor();

  @Test
  void shouldPrefixContextPathForPhysicalTenantRequest() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final ModelAndView modelAndView = new ModelAndView("operate/index");
    modelAndView.addObject("contextPath", "/operate/");

    // when
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), modelAndView);

    // then
    assertThat(modelAndView.getModel().get("contextPath"))
        .isEqualTo("/physical-tenants/tenanta/operate/");
  }

  @Test
  void shouldPrefixContextPathForDefaultPhysicalTenant() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "default");
    final ModelAndView modelAndView = new ModelAndView("tasklist/index");
    modelAndView.addObject("contextPath", "/tasklist/");

    // when
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), modelAndView);

    // then
    assertThat(modelAndView.getModel().get("contextPath"))
        .isEqualTo("/physical-tenants/default/tasklist/");
  }

  @Test
  void shouldInsertPrefixAfterServletContextPath() {
    // given — a non-empty servlet context path; the index controller's contextPath includes it
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContextPath("/camunda");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final ModelAndView modelAndView = new ModelAndView("operate/index");
    modelAndView.addObject("contextPath", "/camunda/operate/");

    // when
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), modelAndView);

    // then — the PT segment is inserted after the context path, not before it
    assertThat(modelAndView.getModel().get("contextPath"))
        .isEqualTo("/camunda/physical-tenants/tenanta/operate/");
  }

  @Test
  void shouldNotChangeContextPathForClusterRequest() {
    // given — no physical tenant id stamped on the request
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final ModelAndView modelAndView = new ModelAndView("operate/index");
    modelAndView.addObject("contextPath", "/operate/");

    // when
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), modelAndView);

    // then
    assertThat(modelAndView.getModel().get("contextPath")).isEqualTo("/operate/");
  }

  @Test
  void shouldNoOpWhenNoModelAndView() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");

    // when / then — no model to mutate, must not throw
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), null);
  }

  @Test
  void shouldNoOpWhenModelHasNoContextPath() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final ModelAndView modelAndView = new ModelAndView("operate/index");

    // when
    interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), modelAndView);

    // then
    assertThat(modelAndView.getModel()).doesNotContainKey("contextPath");
  }
}
