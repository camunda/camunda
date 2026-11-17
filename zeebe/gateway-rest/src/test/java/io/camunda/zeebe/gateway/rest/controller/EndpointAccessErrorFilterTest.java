/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANT_URI_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.pattern.PathPatternParser;

class EndpointAccessErrorFilterTest {

  /** Blocks the groups paths only for the tenants that take their groups from a claim. */
  private final EndpointAccessErrorFilter filter =
      new EndpointAccessErrorFilter(
          new ObjectMapper(),
          "disabled",
          List.of(
              PathPatternParser.defaultInstance.parse("/v2/groups/**"),
              PathPatternParser.defaultInstance.parse(
                  PHYSICAL_TENANT_URI_PREFIX + "/v2/groups/**")),
          Set.of("default", "tenantb")::contains);

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "/v2/groups/g1, 403",
    "/physical-tenants/tenantb/v2/groups/g1, 403",
    "/physical-tenants/tenanta/v2/groups/g1, 200",
    "/v2/users/u1, 200"
  })
  void shouldBlockOnlyTheTenantsThePredicateAccepts(final String path, final int status)
      throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", path);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, new MockFilterChain());

    // then
    assertThat(response.getStatus()).isEqualTo(status);
  }
}
