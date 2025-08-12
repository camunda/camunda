/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      TasklistProfileService.class,
      ClientConfig.class,
      ClientConfigRestService.class,
      SecurityConfiguration.class,
      TasklistPropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    properties = {
      TasklistProperties.PREFIX + ".enterprise=true",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "CAMUNDA_TASKLIST_CLOUD_ORGANIZATIONID=organizationId",
      // CAMUNDA_TASKLIST_CLOUD_CLUSTERID=clusterId  -- leave out to test for null values
      "CAMUNDA_TASKLIST_CLOUD_STAGE=stage",
      "CAMUNDA_TASKLIST_CLOUD_MIXPANELTOKEN=i-am-a-token",
      "CAMUNDA_TASKLIST_CLOUD_MIXPANELAPIHOST=https://fake.mixpanel.com",
      "management.endpoint.health.group.readiness.include=readinessState"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientConfigRestServiceIT {

  @Autowired private WebApplicationContext context;

  @InjectMocks private SecurityConfiguration securityConfiguration;

  private MockMvc mockMvc;

  @BeforeEach
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void testGetClientConfig() throws Exception {
    // when
    final MockHttpServletRequestBuilder request = get("/tasklist/client-config.js");
    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/javascript"))
            .andReturn();

    // then
    assertThat(mvcResult.getResponse().getContentAsString())
        .isEqualTo(
            "window.clientConfig = {"
                + "\"isEnterprise\":true,"
                + "\"isMultiTenancyEnabled\":false,"
                + "\"canLogout\":true,"
                + "\"isLoginDelegated\":false,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/tasklist\","
                + "\"clientMode\":\"v1\","
                + "\"organizationId\":\"organizationId\","
                + "\"clusterId\":null,"
                + "\"stage\":\"stage\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"isResourcePermissionsEnabled\":false,"
                + "\"isUserAccessRestrictionsEnabled\":true,"
                + "\"maxRequestSize\":10485760};");
  }
}
