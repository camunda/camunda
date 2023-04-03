/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      TasklistProfileService.class,
      ClientConfig.class,
      ClientConfigRestService.class,
      TasklistProperties.class
    },
    properties = {
      TasklistProperties.PREFIX + ".enterprise=true",
      "CAMUNDA_TASKLIST_CLOUD_ORGANIZATIONID=organizationId",
      // CAMUNDA_TASKLIST_CLOUD_CLUSTERID=clusterId  -- leave out to test for null values
      "CAMUNDA_TASKLIST_CLOUD_STAGE=stage",
      "CAMUNDA_TASKLIST_CLOUD_MIXPANELTOKEN=i-am-a-token",
      "CAMUNDA_TASKLIST_CLOUD_MIXPANELAPIHOST=https://fake.mixpanel.com"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientConfigRestServiceIT {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void testGetClientConfig() throws Exception {
    // when
    final MockHttpServletRequestBuilder request = get("/client-config.js");
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
                + "\"canLogout\":true,"
                + "\"isLoginDelegated\":false,"
                + "\"contextPath\":\"\","
                + "\"organizationId\":\"organizationId\","
                + "\"clusterId\":null,"
                + "\"stage\":\"stage\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"isResourcePermissionsEnabled\":false};");
  }
}
