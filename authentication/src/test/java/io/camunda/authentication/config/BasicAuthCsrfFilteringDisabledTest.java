/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.security.csrf.enabled=false"
    })
public class BasicAuthCsrfFilteringDisabledTest extends AbstractWebSecurityConfigTest {

  @Test
  public void shouldNotRequireCsrfTokenWithSessionAuthentication() {
    // given
    final MockHttpSession mockHttpSession = new MockHttpSession();

    // when
    final MvcTestResult result =
        mockMvcTester
            .post()
            .session(mockHttpSession)
            .with(user("demo"))
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatusOk();
  }
}
