/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.security.configuration.secureheaders.ContentSecurityPolicyConfig;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** See {@link OidcWebSecurityConfigTest} for scope and limitations of this test class. */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "logging.level.org.springframework.security=DEBUG",
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com",
      "camunda.security.saas.cluster-id=foo",
      "camunda.security.saas.organization-id=bar"
    })
public class OidcSaaSWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri(endpoint)
            .with(SecurityMockMvcRequestPostProcessors.oidcLogin())
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertThat(testResult)
        .headers()
        .contains(
            entry(
                CONTENT_SECURITY_POLICY,
                List.of(ContentSecurityPolicyConfig.DEFAULT_SAAS_SECURITY_POLICY)));
  }
}
