/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Writer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class WebappRedirectStrategyTest {

  private ObjectMapper objectMapper;
  private WebappRedirectStrategy redirectStrategy;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    redirectStrategy = new WebappRedirectStrategy(objectMapper);
  }

  @Test
  void shouldSetNoContentWhenUrlIsNull() throws IOException {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    redirectStrategy.sendRedirect(request, response, null);

    assertThat(response.getStatus()).isEqualTo(NO_CONTENT.value());
  }

  @Test
  void shouldSetNoContentWhenUrlIsDefault() throws IOException {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    redirectStrategy.sendRedirect(request, response, "/");

    assertThat(response.getStatus()).isEqualTo(NO_CONTENT.value());
  }

  @Test
  void shouldSetUrlWhenUrlIsPresentAndReturnOk() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final String url = "/some/valid/redirect";

    redirectStrategy.sendRedirect(request, response, url);

    assertThat(response.getStatus()).isEqualTo(OK.value());
    assertThat(response.getHeader("Content-Type")).isEqualTo("application/json");

    assertThat(objectMapper.readTree(response.getContentAsString()).get("url").asText())
        .isEqualTo(url);
  }

  @Test
  void shouldPropagateExceptionWhenObjectMapperFails() throws Exception {
    final ObjectMapper failingMapper = mock(ObjectMapper.class);
    final WebappRedirectStrategy failingStrategy = new WebappRedirectStrategy(failingMapper);

    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    doThrow(new IOException("Some exception"))
        .when(failingMapper)
        .writeValue(any(Writer.class), any());

    assertThatThrownBy(
            () -> failingStrategy.sendRedirect(request, response, "/some/valid/redirect"))
        .isInstanceOf(IOException.class);
  }
}
