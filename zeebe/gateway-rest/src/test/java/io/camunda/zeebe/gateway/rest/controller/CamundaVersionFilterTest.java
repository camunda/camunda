/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.controller.CamundaVersionFilter.CAMUNDA_VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.util.VersionUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link CamundaVersionFilter}. */
@ExtendWith(MockitoExtension.class)
class CamundaVersionFilterTest {

  @Mock private FilterChain chain;

  private final CamundaVersionFilter filter = new CamundaVersionFilter();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v2",
        "/v2/",
        "/v2/topology",
        "/v2/status",
        "/v2/process-instances/search",
        "/physical-tenants/default/v2/status",
        "/physical-tenants/tenanta/v2/users",
      })
  void shouldSetCamundaVersionOnOrchestrationClusterRestPaths(final String uri) throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", uri);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldSetCamundaVersionWhenServletContextPathIsConfigured() throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", "/zeebe/v2/topology");
    request.setContextPath("/zeebe");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/swagger-ui/index.html",
        "/v1/topology",
        "/physical-tenants/tenanta/actuator/health",
        "/physical-tenants/",
        "/v20/topology",
      })
  void shouldNotSetCamundaVersionOnNonRestPaths(final String uri) throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", uri);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isNull();
    verify(chain).doFilter(request, response);
  }
}
