/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.modules;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.WebappModuleConfiguration;
import io.camunda.operate.util.apps.modules.ModulesTestApplication;
import io.camunda.operate.webapp.controllers.OperateIndexController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@TestPropertySource(
    properties = {"camunda.mode=all-in-one", "camunda.webapps.operate.enabled=false"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      ModulesTestApplication.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    })
public class ModesWithOperateDisabledIT extends ModuleAbstractIT {

  @LocalServerPort private int port;
  @Autowired private WebApplicationContext context;

  private TestRestTemplate restTemplate;
  private MockMvc mockMvc;

  @Before
  public void setUp() {
    restTemplate = new TestRestTemplate();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void testWebappModuleConfigurationIsMissing() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
        .isThrownBy(() -> context.getBean(WebappModuleConfiguration.class));
  }

  @Test
  public void testOperateIndexControllerIsMissing() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
        .isThrownBy(() -> context.getBean(OperateIndexController.class));
  }

  @Test
  public void testOperatePageReturnsError() {
    final String baseUrl = "http://localhost:" + port;
    final ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl + "/operate", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void testOperateApiReturnsError() throws Exception {
    final MvcResult result =
        mockMvc
            .perform(post("/v1/process-instances/search"))
            .andExpect(status().isNotFound())
            .andReturn();
  }
}
