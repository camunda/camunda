/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.spring.authz.AuthorizationCheckerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthorizationScopeRepositoryConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(AuthorizationReader.class, () -> mock(AuthorizationReader.class))
          .withUserConfiguration(AuthorizationScopeRepositoryConfiguration.class)
          .withConfiguration(AutoConfigurations.of(AuthorizationCheckerConfiguration.class));

  @Test
  void shouldRegisterAuthorizationCheckerBeanViaUmbrella() {
    // given — port defined in regular @Configuration, checker provided by CSL umbrella auto-config

    // when / then
    runner.run(ctx -> assertThat(ctx).hasSingleBean(AuthorizationChecker.class));
  }
}
