/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>A minimal {@link TestController} is wired via {@code standaloneSetup} so that each test can
 * trigger a specific Spring MVC exception without starting the full application context.
 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // -------------------------------------------------------------------------
  // Minimal controller used only to provoke specific Spring MVC exceptions.

  record Body(String field) {}

  @RestController
  @RequestMapping("/test")
  static class TestController {

    /** Endpoint with a required query parameter — omitting it triggers MissingParam. */
    @GetMapping("/required-param")
    String withRequiredParam(@RequestParam final String required) {
      return required;
    }

    /** Endpoint with a typed parameter — sending a non-numeric value triggers TypeMismatch. */
    @GetMapping("/typed-param")
    String withTypedParam(@RequestParam final int count) {
      return String.valueOf(count);
    }

    /** Endpoint with a JSON body — sending malformed JSON triggers HttpMessageNotReadable. */
    @PostMapping("/body")
    String withBody(@RequestBody final Body body) {
      return body.field();
    }

    /**
     * Endpoint that always throws — verifies the catch-all handler maps unknown exceptions to 500.
     */
    @PostMapping("/unexpected-error")
    String withUnexpectedError() {
      throw new RuntimeException("boom");
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class MissingRequiredParam {

    @Test
    void shouldReturn400WithInvalidRequestError() throws Exception {
      // given: required query parameter 'required' is absent
      // when
      mockMvc
          .perform(get("/test/required-param"))
          // then
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.message").value("Required parameter 'required' is missing"));
    }
  }

  @Nested
  class TypeMismatchParam {

    @Test
    void shouldReturn400WithInvalidRequestError() throws Exception {
      // given: 'count' expects an int but receives a non-numeric string
      // when
      mockMvc
          .perform(get("/test/typed-param").param("count", "not-a-number"))
          // then
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.message").value("Parameter 'count' must be a valid int"));
    }
  }

  @Nested
  class UnreadableBody {

    @Test
    void shouldReturn400WithInvalidRequestErrorForMalformedJson() throws Exception {
      // given: body contains invalid JSON that cannot be deserialized
      // when
      mockMvc
          .perform(
              post("/test/body")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad json"))
          // then
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }
  }

  @Nested
  class MethodNotAllowed {

    @Test
    void shouldReturn405WhenWrongHttpMethodIsUsed() throws Exception {
      // given: /test/required-param only accepts GET; sending POST triggers method-not-allowed
      // when
      mockMvc
          .perform(post("/test/required-param").param("required", "x"))
          // then
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
          .andExpect(jsonPath("$.message").value("HTTP method 'POST' is not supported for this endpoint"));
    }
  }

  @Nested
  class UnexpectedException {

    @Test
    void shouldReturn500WithInternalErrorAndNoStackTrace() throws Exception {
      // given: the handler throws an unhandled RuntimeException
      // when
      mockMvc
          .perform(post("/test/unexpected-error"))
          // then
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
          .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
  }
}
