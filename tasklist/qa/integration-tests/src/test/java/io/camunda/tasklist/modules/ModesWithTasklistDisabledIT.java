/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.modules;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.WebappModuleConfiguration;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.webapp.controllers.TasklistIndexController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@TestPropertySource(
    properties = {"camunda.mode=all-in-one", "camunda.webapps.tasklist.enabled=false"})
public class ModesWithTasklistDisabledIT extends ModuleIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper objectMapper;

  private TestRestTemplate restTemplate;
  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    restTemplate = new TestRestTemplate();
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void testWebappModuleConfigurationIsMissing() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
        .isThrownBy(() -> applicationContext.getBean(WebappModuleConfiguration.class));
  }

  @Test
  public void testTasklistIndexControllerIsMissing() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
        .isThrownBy(() -> applicationContext.getBean(TasklistIndexController.class));
  }

  @Test
  public void testTasklistPageReturnsError() {
    final String baseUrl = "http://localhost:" + port;
    final ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl + "/tasklist", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void testTasklistApiReturnsError() {
    final var result = mockMvcHelper.doRequest(post("/v1/tasks/search"));
    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }
}
