/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CCSMErrorAttributesTest {

  private static final String ERROR_MESSAGE =
      "User has no authorization to access Optimize. Please check your Identity configuration";

  private final CCSMErrorAttributes errorAttributes = new CCSMErrorAttributes();

  @Test
  void shouldIncludeAuthenticationErrorMessageInErrorPage() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, ERROR_MESSAGE);

    // when
    final var attributes =
        errorAttributes.getErrorAttributes(
            new ServletWebRequest(request), ErrorAttributeOptions.defaults());

    // then
    assertThat(attributes).containsEntry("message", ERROR_MESSAGE);
  }

  @Test
  void shouldNotIncludeMessagesForOtherErrors() {
    // given
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "internal details");

    // when
    final var attributes =
        errorAttributes.getErrorAttributes(
            new ServletWebRequest(request), ErrorAttributeOptions.defaults());

    // then
    assertThat(attributes).doesNotContainKey("message");
  }
}
