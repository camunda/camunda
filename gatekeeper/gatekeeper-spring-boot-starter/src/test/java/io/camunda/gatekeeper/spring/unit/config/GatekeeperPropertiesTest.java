/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spring.config.GatekeeperProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

final class GatekeeperPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void shouldBindDefaultProperties() {
    contextRunner.run(
        context -> {
          final GatekeeperProperties props = context.getBean(GatekeeperProperties.class);
          assertThat(props.getAuthentication().getMethod()).isEqualTo("basic");
          assertThat(props.getAuthentication().isUnprotectedApi()).isFalse();
          assertThat(props.getAuthentication().getAuthenticationRefreshInterval())
              .isEqualTo("PT30S");
        });
  }

  @Test
  void shouldBindOidcProperties() {
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.oidc.issuer-uri=https://issuer.example.com",
            "camunda.security.authentication.oidc.client-id=my-client",
            "camunda.security.authentication.oidc.client-secret=secret",
            "camunda.security.authentication.oidc.jwk-set-uri=https://issuer.example.com/jwks",
            "camunda.security.authentication.oidc.username-claim=email",
            "camunda.security.authentication.oidc.groups-claim=roles",
            "camunda.security.authentication.oidc.prefer-username-claim=true",
            "camunda.security.authentication.oidc.audiences[0]=aud1",
            "camunda.security.authentication.oidc.audiences[1]=aud2",
            "camunda.security.authentication.oidc.additional-jwk-set-uris[0]=https://extra.example.com/jwks")
        .run(
            context -> {
              final GatekeeperProperties props = context.getBean(GatekeeperProperties.class);
              final var oidc = props.getAuthentication().getOidc();
              assertThat(oidc.getIssuerUri()).isEqualTo("https://issuer.example.com");
              assertThat(oidc.getClientId()).isEqualTo("my-client");
              assertThat(oidc.getClientSecret()).isEqualTo("secret");
              assertThat(oidc.getJwkSetUri()).isEqualTo("https://issuer.example.com/jwks");
              assertThat(oidc.getUsernameClaim()).isEqualTo("email");
              assertThat(oidc.getGroupsClaim()).isEqualTo("roles");
              assertThat(oidc.isPreferUsernameClaim()).isTrue();
              assertThat(oidc.getAudiences()).containsExactly("aud1", "aud2");
              assertThat(oidc.getAdditionalJwkSetUris())
                  .containsExactly("https://extra.example.com/jwks");
            });
  }

  @Test
  void shouldConvertToAuthenticationConfig() {
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.authentication-refresh-interval=PT60S",
            "camunda.security.authentication.unprotected-api=true",
            "camunda.security.authentication.oidc.issuer-uri=https://issuer.example.com",
            "camunda.security.authentication.oidc.client-id=my-client",
            "camunda.security.authentication.oidc.clock-skew=PT120S")
        .run(
            context -> {
              final GatekeeperProperties props = context.getBean(GatekeeperProperties.class);
              final AuthenticationConfig config = props.toAuthenticationConfig();
              assertThat(config.method()).isEqualTo(AuthenticationMethod.OIDC);
              assertThat(config.authenticationRefreshInterval()).isEqualTo(Duration.ofSeconds(60));
              assertThat(config.unprotectedApi()).isTrue();
              assertThat(config.oidc().issuerUri()).isEqualTo("https://issuer.example.com");
              assertThat(config.oidc().clientId()).isEqualTo("my-client");
              assertThat(config.oidc().clockSkew()).isEqualTo(Duration.ofSeconds(120));
            });
  }

  @Test
  void shouldProduceCorrectDefaultDomainConfigs() {
    contextRunner.run(
        context -> {
          final GatekeeperProperties props = context.getBean(GatekeeperProperties.class);

          final AuthenticationConfig authConfig = props.toAuthenticationConfig();
          assertThat(authConfig.method()).isEqualTo(AuthenticationMethod.BASIC);
          assertThat(authConfig.authenticationRefreshInterval()).isEqualTo(Duration.ofSeconds(30));
          assertThat(authConfig.unprotectedApi()).isFalse();
          assertThat(authConfig.oidc().clockSkew()).isEqualTo(Duration.ofSeconds(60));
          assertThat(authConfig.oidc().idpLogoutEnabled()).isTrue();
          assertThat(authConfig.oidc().grantType()).isEqualTo("authorization_code");
          assertThat(authConfig.oidc().clientAuthenticationMethod())
              .isEqualTo("client_secret_basic");
          assertThat(authConfig.oidc().usernameClaim()).isEqualTo("sub");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(GatekeeperProperties.class)
  static class TestConfig {}
}
