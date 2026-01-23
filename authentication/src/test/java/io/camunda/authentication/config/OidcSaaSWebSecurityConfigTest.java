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

import io.camunda.authentication.config.controllers.OidcMockMvcTestHelper;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.security.configuration.headers.ContentSecurityPolicyConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** See {@link OidcWebSecurityConfigTest} for scope and limitations of this test class. */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
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

  @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + endpoint)
            .with(OidcMockMvcTestHelper.oidcLogin(authorizedClientRepository))
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertThat(testResult)
        .headers()
        .hasValue(
            CONTENT_SECURITY_POLICY, ContentSecurityPolicyConfig.DEFAULT_SAAS_SECURITY_POLICY);
  }
}
