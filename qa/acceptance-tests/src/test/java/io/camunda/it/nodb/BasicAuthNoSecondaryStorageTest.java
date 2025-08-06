/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.nodb;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.authentication.exception.BasicAuthenticationNotSupportedException;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Acceptance test to verify that Basic Authentication fails fast when configured with no secondary
 * storage. This test validates that the application startup fails with a clear error when
 * database.type=none and Basic Authentication is configured.
 */
@ZeebeIntegration
public class BasicAuthNoSecondaryStorageTest {

  @Test
  void shouldFailToStartWithBasicAuthAndNoSecondaryStorage() {
    // given - Basic Authentication configured with no secondary storage
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE);

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(BasicAuthenticationNotSupportedException.class);
  }
}
