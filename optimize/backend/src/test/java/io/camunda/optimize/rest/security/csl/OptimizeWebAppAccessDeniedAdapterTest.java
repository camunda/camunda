/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OptimizeWebAppAccessDeniedAdapterTest {

  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(builder -> builder.user("kermit"));

  private final OptimizeWebAppAccessDeniedAdapter adapter = new OptimizeWebAppAccessDeniedAdapter();

  @Test
  void shouldAnswerApiCallsWithUnauthorizedSoTheAppReloads() throws Exception {
    // given
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    adapter.handle(
        new MockHttpServletRequest("GET", "/api/entities"), response, "optimize", AUTHENTICATION);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void shouldAnswerNavigationsWithForbiddenSoTheErrorPageRenders() throws Exception {
    // given
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    adapter.handle(new MockHttpServletRequest("GET", "/"), response, "optimize", AUTHENTICATION);

    // then
    assertThat(response.getStatus()).isEqualTo(403);
  }
}
