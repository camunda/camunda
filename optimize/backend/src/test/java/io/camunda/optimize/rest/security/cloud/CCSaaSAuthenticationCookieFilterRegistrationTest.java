/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.rest.security.AuthenticationCookieFilter;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;

class CCSaaSAuthenticationCookieFilterRegistrationTest {

  private final ApplicationContextRunner ccsaasRunner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.profiles.active=cloud")
          .withBean(
              LazyInitializationBeanFactoryPostProcessor.class,
              LazyInitializationBeanFactoryPostProcessor::new)
          .withBean(
              ConfigurationService.class,
              CCSaaSAuthenticationCookieFilterRegistrationTest::cloudConfiguration)
          .withBean(
              CustomPreAuthenticatedAuthenticationProvider.class,
              () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
          .withBean(SessionService.class, () -> mock(SessionService.class))
          .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
          .withBean(UserIdMigrationService.class, () -> mock(UserIdMigrationService.class))
          .withUserConfiguration(
              CCSaaSSecurityConfigurerAdapter.class, CCSaasAuth0WebSecurityConfig.class);

  @Test
  void shouldNotMapAuthenticationCookieFilterOntoEveryRequest() {
    // The adapter wires this filter into its OAuth2 chain by hand. As a bare Filter bean, Spring
    // Boot would also map it onto /*, running it on every request and twice inside the chain
    // (camunda/camunda#63396). ServletContextInitializerBeans is what Boot uses to decide.
    ccsaasRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          final AuthenticationCookieFilter filter =
              context.getBean(AuthenticationCookieFilter.class);
          assertThat(new ServletContextInitializerBeans(context.getBeanFactory()))
              .filteredOn(
                  initializer ->
                      initializer instanceof final FilterRegistrationBean<?> registration
                          && registration.getFilter() == filter)
              .singleElement()
              .satisfies(
                  registration ->
                      assertThat(((FilterRegistrationBean<?>) registration).isEnabled()).isFalse());
        });
  }

  private static ConfigurationService cloudConfiguration() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    final var cloudAuthConfiguration =
        configurationService.getAuthConfiguration().getCloudAuthConfiguration();
    cloudAuthConfiguration.setClientId("auth0-client");
    cloudAuthConfiguration.setClientSecret("auth0-secret");
    cloudAuthConfiguration.setOrganizationId("org-1");
    // The adapter's JWT decoders reject a blank JWKS URI when its chains are built. They never
    // fetch it during the build, so any absolute URL will do.
    configurationService
        .getOptimizeApiConfiguration()
        .setJwtSetUri("https://idp.example.com/.well-known/jwks.json");
    return configurationService;
  }
}
