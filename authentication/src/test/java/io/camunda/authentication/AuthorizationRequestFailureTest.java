/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.handler.OAuth2AuthenticationExceptionHandler;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;

/**
 * This test ensures that the error code defined in our {@link
 * io.camunda.authentication.handler.OAuth2AuthenticationExceptionHandler} matches the one used
 * internally by Spring Security's OAuth2LoginAuthenticationFilter. This is important because if
 * they diverge, our custom error handling logic may not work as intended.
 */
class AuthorizationRequestFailureTest {

  @Test
  public void shouldSendClientAssertionToTokenEndpointDuringAuthCodeExchange() {
    final Field springAuthorizationRequestErrorCode;
    try {
      springAuthorizationRequestErrorCode =
          OAuth2LoginAuthenticationFilter.class.getDeclaredField(
              "AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE");
      springAuthorizationRequestErrorCode.setAccessible(true);
      assertThat(springAuthorizationRequestErrorCode.get(String.class))
          .isEqualTo(
              OAuth2AuthenticationExceptionHandler.AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
