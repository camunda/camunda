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
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spring.autoconfigure.GatekeeperAuthAutoConfiguration;
import io.camunda.gatekeeper.spring.autoconfigure.GatekeeperBasicAuthAutoConfiguration;
import io.camunda.gatekeeper.spring.converter.UsernamePasswordAuthenticationTokenConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GatekeeperBasicAuthAutoConfigurationTest {

  private final WebApplicationContextRunner basicRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GatekeeperAuthAutoConfiguration.class,
                  GatekeeperBasicAuthAutoConfiguration.class))
          .withPropertyValues("camunda.security.authentication.method=basic")
          .withUserConfiguration(
              MembershipResolverConfiguration.class, ObjectMapperConfiguration.class);

  private final WebApplicationContextRunner oidcRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GatekeeperAuthAutoConfiguration.class,
                  GatekeeperBasicAuthAutoConfiguration.class))
          .withPropertyValues("camunda.security.authentication.method=oidc")
          .withUserConfiguration(ObjectMapperConfiguration.class);

  @Test
  void shouldCreateBasicAuthConverterWhenMethodIsBasic() {
    basicRunner.run(
        context -> {
          final var converters = context.getBeansOfType(CamundaAuthenticationConverter.class);
          assertThat(converters.values())
              .anyMatch(c -> c instanceof UsernamePasswordAuthenticationTokenConverter);
        });
  }

  @Test
  void shouldNotCreateBasicAuthConverterWhenMethodIsOidc() {
    oidcRunner.run(
        context -> {
          final var converters = context.getBeansOfType(CamundaAuthenticationConverter.class);
          assertThat(converters.values())
              .noneMatch(c -> c instanceof UsernamePasswordAuthenticationTokenConverter);
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
}
