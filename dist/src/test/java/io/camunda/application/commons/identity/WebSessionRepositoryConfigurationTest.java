/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.lang.Thread.UncaughtExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class WebSessionRepositoryConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withBean(ConnectConfiguration.class, ConnectConfiguration::new)
          .withBean(
              PersistentWebSessionDbReader.class, () -> mock(PersistentWebSessionDbReader.class))
          .withBean(PersistentWebSessionWriter.class, () -> mock(PersistentWebSessionWriter.class))
          .withUserConfiguration(WebSessionRepositoryConfiguration.class)
          .withPropertyValues("camunda.data.secondary-storage.type=rdbms");

  @Test
  void shouldWireHostFatalErrorHandlerWhenPersistentSessionsEnabled() {
    runner
        .withPropertyValues("camunda.security.session.persistent.enabled=true")
        .run(
            ctx ->
                assertThat(ctx)
                    .getBean(
                        "webSessionDeletionUncaughtExceptionHandler",
                        UncaughtExceptionHandler.class)
                    .satisfies(
                        handler ->
                            assertThat(handler.getClass().getSimpleName())
                                .as(
                                    "expected host's FatalErrorHandler-backed handler; got %s",
                                    handler.getClass().getName())
                                .isEqualTo("VirtualMachineErrorHandler")));
  }

  @Test
  void shouldNotRegisterWebSessionBeansWhenPersistentSessionsDisabled() {
    runner
        .withPropertyValues("camunda.security.session.persistent.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean("webSessionDeletionUncaughtExceptionHandler"));
  }
}
