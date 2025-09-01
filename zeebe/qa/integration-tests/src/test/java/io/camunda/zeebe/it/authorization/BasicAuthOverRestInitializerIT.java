/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class BasicAuthOverRestInitializerIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoClose private static CamundaClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withSecurityConfig(
              camundaSecurityProperties -> {
                camundaSecurityProperties
                    .getAuthentication()
                    .setOidc(
                        OidcAuthenticationConfiguration.builder().usernameClaim("sub1").build());
              });

  @Test
  public void shouldFailToStartWithOidcAuthenticationConfigured() {
    // given
    final String expectedMessage =
        "Oidc configuration is not supported with `BASIC` authentication method";
    // when
    final Throwable throwable = Assertions.assertThatThrownBy(broker::start).actual();
    // then
    assertHasNestedException(throwable, IllegalStateException.class, expectedMessage);
  }

  private void assertHasNestedException(
      Throwable exception, final Class<?> expectedClass, final String expectedMessage) {
    while (exception != null) {
      if (expectedClass.equals(exception.getClass())
          && expectedMessage.equals(exception.getMessage())) {
        return;
      }
      exception = exception.getCause();
    }
    Fail.fail(
        "Exception has no cause with expectedClass: "
            + expectedClass.getSimpleName()
            + " and message: "
            + expectedMessage);
  }
}
