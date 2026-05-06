/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomCssControllerTest {

  private CustomCssController controller;

  @Mock private ClassPathResource mockResource;

  @BeforeEach
  void setUp() {
    controller = spy(new CustomCssController());
  }

  @Test
  void shouldLoadCustomCssOnInit() throws IOException {
    // given
    final String cssContent = "body { background-color: #f3f3f3; }";
    doReturn(mockResource).when(controller).loadResource(CustomCssController.CLASSPATH_LOCATION);
    when(mockResource.exists()).thenReturn(true);
    when(mockResource.getContentAsString(any())).thenReturn(cssContent);

    // when
    controller.init();

    // then
    assertThat(controller.getCustomCssContent()).isEqualTo(cssContent);
  }

  @Test
  void shouldReturnEmptyStringWhenFileNotPresent() {
    // given
    doReturn(mockResource).when(controller).loadResource(CustomCssController.CLASSPATH_LOCATION);
    when(mockResource.exists()).thenReturn(false);

    // when
    controller.init();

    // then — missing file is expected in most deployments; must not throw or log ERROR
    assertThat(controller.getCustomCssContent()).isEmpty();
  }

  @Test
  void shouldFallbackToEmptyStringOnReadError() throws IOException {
    // given
    doReturn(mockResource).when(controller).loadResource(CustomCssController.CLASSPATH_LOCATION);
    when(mockResource.exists()).thenReturn(true);
    doThrow(new IOException("disk error")).when(mockResource).getContentAsString(any());

    // when
    controller.init();

    // then — genuine read failure degrades gracefully
    assertThat(controller.getCustomCssContent()).isEmpty();
  }

  @Test
  void shouldReturn200WithCssContent() throws IOException {
    // given
    final String cssContent = "h1 { color: red; }";
    doReturn(mockResource).when(controller).loadResource(any());
    when(mockResource.exists()).thenReturn(true);
    when(mockResource.getContentAsString(any())).thenReturn(cssContent);
    controller.init();

    // when
    final var response = controller.getCustomCss();

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(cssContent);
  }

  @Test
  void shouldSetNoCacheHeader() throws IOException {
    // given
    doReturn(mockResource).when(controller).loadResource(any());
    when(mockResource.exists()).thenReturn(true);
    when(mockResource.getContentAsString(any())).thenReturn("");
    controller.init();

    // when
    final var response = controller.getCustomCss();

    // then — operators may swap the file between deployments; stale caches must not persist
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
  }
}
