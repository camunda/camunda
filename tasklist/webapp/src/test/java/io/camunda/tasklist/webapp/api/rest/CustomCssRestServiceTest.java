/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.tasklist.webapp.rest.CustomCssRestService;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
public class CustomCssRestServiceTest {

  @InjectMocks private CustomCssRestService customCssRestService;

  @Mock private Logger logger;

  @Mock private ClassPathResource mockResource;

  private final String cssContent = "body { background-color: #f3f3f3; }";
  private final String resourceLocation = "custom.css";

  @BeforeEach
  void setUp() {
    // Inject the mock logger to avoid actual logging during tests
    customCssRestService = spy(new CustomCssRestService());
  }

  @Test
  void initShouldLoadCustomCssContent() throws IOException {
    doReturn(mockResource).when(customCssRestService).loadResource(resourceLocation);
    when(mockResource.getContentAsString(any())).thenReturn(cssContent);

    customCssRestService.init();

    assertThat(customCssRestService.getClientConfig()).isEqualTo(cssContent);
  }

  @Test
  void getClientConfigShouldReturnEmptyWhenCssContentIsNull() {
    // Act
    final String result = customCssRestService.getClientConfig();

    // Assert
    assertThat(result).isNull();
  }
}
