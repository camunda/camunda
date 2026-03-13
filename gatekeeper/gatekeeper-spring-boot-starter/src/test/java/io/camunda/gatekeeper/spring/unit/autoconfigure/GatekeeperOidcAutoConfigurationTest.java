/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spring.autoconfigure.GatekeeperAuthAutoConfiguration;
import io.camunda.gatekeeper.spring.autoconfigure.GatekeeperOidcAutoConfiguration;
import io.camunda.gatekeeper.spring.converter.TokenClaimsConverter;
import io.camunda.gatekeeper.spring.oidc.AssertionJwkProvider;
import io.camunda.gatekeeper.spring.oidc.JWSKeySelectorFactory;
import io.camunda.gatekeeper.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.gatekeeper.spring.oidc.OidcAuthenticationConfigurationRepository;
import io.camunda.gatekeeper.spring.oidc.TokenValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class GatekeeperOidcAutoConfigurationTest {

  private final WebApplicationContextRunner oidcRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GatekeeperAuthAutoConfiguration.class, GatekeeperOidcAutoConfiguration.class))
          .withPropertyValues(
              "camunda.security.authentication.method=oidc",
              "camunda.security.authentication.oidc.client-id=test-client",
              "camunda.security.authentication.oidc.issuer-uri=http://localhost:18080/realms/test")
          .withUserConfiguration(
              MembershipResolverConfiguration.class,
              ObjectMapperConfiguration.class,
              TestClientRegistrationConfiguration.class);

  private final WebApplicationContextRunner basicRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GatekeeperAuthAutoConfiguration.class, GatekeeperOidcAutoConfiguration.class))
          .withPropertyValues("camunda.security.authentication.method=basic")
          .withUserConfiguration(ObjectMapperConfiguration.class);

  @Test
  void shouldCreateOidcBeansWhenMethodIsOidc() {
    oidcRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OidcAuthenticationConfigurationRepository.class);
          assertThat(context).hasSingleBean(TokenClaimsConverter.class);
          assertThat(context).hasSingleBean(JWSKeySelectorFactory.class);
          assertThat(context).hasSingleBean(TokenValidatorFactory.class);
          assertThat(context).hasSingleBean(OidcAccessTokenDecoderFactory.class);
          assertThat(context).hasSingleBean(AssertionJwkProvider.class);
        });
  }

  @Test
  void shouldNotCreateOidcBeansWhenMethodIsBasic() {
    basicRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(OidcAuthenticationConfigurationRepository.class);
          assertThat(context).doesNotHaveBean(TokenClaimsConverter.class);
          assertThat(context).doesNotHaveBean(JWSKeySelectorFactory.class);
          assertThat(context).doesNotHaveBean(TokenValidatorFactory.class);
          assertThat(context).doesNotHaveBean(OidcAccessTokenDecoderFactory.class);
          assertThat(context).doesNotHaveBean(AssertionJwkProvider.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class MembershipResolverConfiguration {
    @Bean
    public MembershipResolver membershipResolver() {
      return (claims, principalName, principalType) -> null;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ObjectMapperConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  /**
   * Provides a test ClientRegistrationRepository to avoid network calls during OIDC
   * auto-configuration tests. This backs off the auto-configured one.
   */
  @Configuration(proxyBeanMethods = false)
  static class TestClientRegistrationConfiguration {
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
      final var clientRegistration =
          ClientRegistration.withRegistrationId("oidc")
              .clientId("test-client")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/sso-callback")
              .authorizationUri("http://localhost:18080/realms/test/protocol/openid-connect/auth")
              .tokenUri("http://localhost:18080/realms/test/protocol/openid-connect/token")
              .jwkSetUri("http://localhost:18080/realms/test/protocol/openid-connect/certs")
              .issuerUri("http://localhost:18080/realms/test")
              .build();
      return new InMemoryClientRegistrationRepository(List.of(clientRegistration));
    }
  }
}
