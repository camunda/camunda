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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.spring.handler.JsonProblemDetailAuthFailureHandler;
import io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration;
import io.camunda.zeebe.util.VersionUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

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
    verify(chain).doFilter(eq(request), any(HttpServletResponseWrapper.class));
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
    verify(chain).doFilter(eq(request), any(HttpServletResponseWrapper.class));
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v%32/topology",
        "/v%32/",
        "/physical-tenants/default/v%32/topology",
        "/physical-tenants/tenanta/v%32/users",
      })
  void shouldSetCamundaVersionOnPercentEncodedV2Paths(final String uri) throws Exception {
    // given — Spring routes the decoded path; clients that encode a path segment must still
    // see Camunda-Version or they will treat this cluster as one that predates the header
    final var request = new MockHttpServletRequest("GET", uri);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
    verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
  }

  @Test
  void shouldNotSetCamundaVersionOnPercentEncodedNonV2Path() throws Exception {
    // /v%31/topology decodes to /v1/topology — not Orchestration Cluster REST
    final var request = new MockHttpServletRequest("GET", "/v%31/topology");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isNull();
    verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
  }

  @Test
  void shouldContinueTheChainWhenThePathHasMalformedPercentEncoding() {
    // UrlPathHelper throws on invalid sequences; the filter must not turn that into a 500
    final var request = new MockHttpServletRequest("GET", "/v2/%zz");
    final var response = new MockHttpServletResponse();

    assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
  }

  @Test
  void shouldPreserveCamundaVersionWhenDownstreamResetsTheResponse() throws Exception {
    // given — JsonProblemDetailAuthFailureHandler (and anything else that rebuilds the
    // response) calls response.reset(), which clears headers already written
    final var request = new MockHttpServletRequest("GET", "/v2/topology");
    final var response = new MockHttpServletResponse();
    final FilterChain resetting =
        (req, res) -> {
          final var http = (HttpServletResponse) res;
          http.reset();
          http.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };

    // when
    filter.doFilter(request, response, resetting);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
  }

  @Test
  void shouldPreserveCamundaVersionWhenAuthFailureHandlerWrites401() throws Exception {
    // given — the real CSL handler, not a stand-in: it is what unauthenticated /v2 calls hit
    final var request = new MockHttpServletRequest("GET", "/v2/topology");
    final var response = new MockHttpServletResponse();
    final var handler = new JsonProblemDetailAuthFailureHandler(new ObjectMapper());
    final FilterChain authFailure =
        (req, res) ->
            handler.commence(
                (HttpServletRequest) req,
                (HttpServletResponse) res,
                new AuthenticationCredentialsNotFoundException("unauthenticated"));

    // when
    filter.doFilter(request, response, authFailure);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
  }

  @Test
  void shouldPassAResponseWrapperToTheRestOfTheChain() throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", "/v2/topology");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then — the wrapper is what lets reset() restore Camunda-Version
    verify(chain).doFilter(eq(request), any(HttpServletResponseWrapper.class));
  }

  @Test
  void shouldSetCamundaVersionOnPhysicalTenantStatusScopeEarlyReturn() throws Exception {
    // given — PhysicalTenantStatusScopeFilter writes 404 and does not continue the chain
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/tenanta/v2/status");
    final var response = new MockHttpServletResponse();
    final var statusScope = new PhysicalTenantStatusScopeFilter();

    // when — version filter first, as the registration order requires
    filter.doFilter(request, response, (req, res) -> statusScope.doFilter(req, res, chain));

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    assertThat(response.getHeader(CAMUNDA_VERSION_HEADER)).isEqualTo(VersionUtil.getVersion());
  }

  @Test
  void shouldRegisterBeforePhysicalTenantStatusScopeFilter() {
    // PhysicalTenantStatusScopeFilter returns 404 without continuing the chain. A tied
    // order that places it first skips this filter, so Camunda-Version must be strictly
    // earlier than the other pre-security filters (and still before Spring Security at -100).
    assertThat(ApiFiltersConfiguration.CAMUNDA_VERSION_FILTER_ORDER)
        .isLessThan(ApiFiltersConfiguration.PHYSICAL_TENANT_FILTER_ORDER)
        .isLessThan(-100);
  }
}
