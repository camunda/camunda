/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ModesAndProfilesProcessorTest {

  @Test
  void shouldSetInsecureSecurityPropertiesUnderTheCorrectKeysWhenInsecureModeIsEnabled() {
    // given a StandaloneCamunda app launched with --camunda.mode=all-in-one --camunda.insecure=true
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.mode", "all-in-one");
    environment.setProperty("camunda.insecure", "true");

    // when the listener prepares the environment
    new ModesAndProfilesProcessor(standaloneCamundaApplication(), new String[] {})
        .environmentPrepared(null, environment);

    // then all four insecure-mode defaults are bound under the keys SecurityConfiguration expects
    assertThat(environment.getProperty("zeebe.broker.gateway.security.enabled", Boolean.class))
        .isFalse();
    assertThat(environment.getProperty("zeebe.gateway.security.enabled", Boolean.class)).isFalse();
    assertThat(
            environment.getProperty(
                "camunda.security.authentication.unprotected-api", Boolean.class))
        .isTrue();
    assertThat(environment.getProperty("camunda.security.authorizations.enabled", Boolean.class))
        .isFalse();
  }

  @Test
  void shouldNotSetInsecureSecurityPropertiesWhenInsecureFlagIsAbsent() {
    // given a StandaloneCamunda app launched with --camunda.mode=all-in-one but no insecure flag
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.mode", "all-in-one");

    // when the listener prepares the environment
    new ModesAndProfilesProcessor(standaloneCamundaApplication(), new String[] {})
        .environmentPrepared(null, environment);

    // then no insecure-mode security defaults are bound
    assertThat(environment.getProperty("zeebe.broker.gateway.security.enabled")).isNull();
    assertThat(environment.getProperty("zeebe.gateway.security.enabled")).isNull();
    assertThat(environment.getProperty("camunda.security.authentication.unprotected-api")).isNull();
    assertThat(environment.getProperty("camunda.security.authorizations.enabled")).isNull();
  }

  private static SpringApplication standaloneCamundaApplication() {
    final SpringApplication application = mock(SpringApplication.class);
    when(application.getMainApplicationClass()).thenReturn((Class) StandaloneCamunda.class);
    return application;
  }
}
