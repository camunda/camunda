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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.WebappModuleConfiguration;
import io.camunda.operate.util.apps.modules.ModulesTestApplication;
import io.camunda.operate.webapp.controllers.OperateIndexController;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@TestPropertySource(
    properties = {"camunda.mode=all-in-one", "camunda.webapps.operate.ui-enabled=false"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      ModulesTestApplication.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    })
public class ModesWithOperateUiDisabledIT extends ModuleAbstractIT {

  @LocalServerPort private int port;
  @Autowired private WebApplicationContext context;
  @MockitoBean private CamundaAuthenticationProvider camundaAuthenticationProvider;
  @MockitoBean private PermissionsService permissionsService;

  private TestRestTemplate restTemplate;
  private MockMvc mockMvc;

  @Before
  public void setUp() {
    restTemplate = new TestRestTemplate();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void testWebappModuleConfigurationIsPresent() {
    assertThatNoException().isThrownBy(() -> context.getBean(WebappModuleConfiguration.class));
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
  public void testOperateApiReturnsOk() throws Exception {
    final var result =
        mockMvc
            .perform(
                post("/v1/process-instances/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn();

    /* If we get this error, it means that the API is working as expected */
    assertThat(result.getResolvedException().getMessage())
        .isEqualTo("Error in reading process instances");
  }
}
