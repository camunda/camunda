/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.security.api.model.config.SessionConfiguration;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.security.spring.session.WebSessionConfiguration;
import io.camunda.security.spring.session.WebSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Pins the contract between CSL's session lifecycle and Optimize's store: {@link
 * WebSessionConfiguration} activates on {@code camunda.security.session.persistent.enabled} and
 * then <em>requires</em> a {@code SessionStorePort}. That coupling is why {@link
 * OptimizeSecurityConfigCompatibilityPostProcessor} may only set the property while every database
 * Optimize supports has a store implementation.
 */
class CslWebSessionWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues("optimize.security.csl.enabled=true")
          .withBean(HttpServletRequest.class, () -> mock(HttpServletRequest.class))
          .withBean(
              PersistentWebSessionRepository.class,
              () -> mock(PersistentWebSessionRepository.class))
          .withUserConfiguration(OptimizeSessionStoreAdapter.class, WebSessionConfiguration.class);

  @Test
  void shouldPersistSessionsThroughOptimizeStoreWhenPersistenceIsEnabled() {
    runner
        .withPropertyValues(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY + "=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              // CSL's repository is what the session filter uses, and it persists through the
              // single SessionStorePort bean, so sessions land in Optimize's index, not in memory
              assertThat(context).hasSingleBean(WebSessionRepository.class);
              assertThat(context).hasSingleBean(SessionStorePort.class);
              assertThat(context.getBean(SessionStorePort.class))
                  .isInstanceOf(OptimizeSessionStoreAdapter.class);
            });
  }

  @Test
  void shouldKeepCslSessionsInMemoryWhenPersistenceIsNotEnabled() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(WebSessionRepository.class);
        });
  }

  @Test
  void shouldFailToStartWhenPersistenceIsEnabledWithoutAStore() {
    // given a context where no store implementation contributed a SessionStorePort bean
    new ApplicationContextRunner()
        .withPropertyValues(
            "optimize.security.csl.enabled=true",
            SessionConfiguration.PERSISTENT_ENABLED_PROPERTY + "=true")
        .withBean(HttpServletRequest.class, () -> mock(HttpServletRequest.class))
        .withUserConfiguration(WebSessionConfiguration.class)
        .run(
            context ->
                // then the context cannot be built, which is why every supported database needs a
                // store before the post processor may enable persistence
                assertThat(context)
                    .getFailure()
                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class));
  }
}
