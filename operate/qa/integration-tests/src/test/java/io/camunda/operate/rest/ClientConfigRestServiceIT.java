/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfig;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ClientConfig.class,
      ClientConfigRestService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class,
      SecurityConfiguration.class
    },
    properties = {
      // OperateProperties.PREFIX + ".cloud.organizationId=organizationId",//  -- leave out to test
      // for null values
      OperateProperties.PREFIX + ".cloud.clusterId=clusterId",
      OperateProperties.PREFIX + ".cloud.mixpanelToken=i-am-a-token",
      OperateProperties.PREFIX + ".cloud.mixpanelAPIHost=https://fake.mixpanel.com"
    })
public class ClientConfigRestServiceIT extends OperateAbstractIT {

  @MockBean private OperateProfileService operateProfileService;

  @Autowired private OperateProperties operateProperties;

  @Test
  public void testGetClientConfig() throws Exception {
    // given
    operateProperties.setTasklistUrl("https://tasklist.camunda.io/tl");
    given(operateProfileService.currentProfileCanLogout()).willReturn(true);
    // when
    final MockHttpServletRequestBuilder request = get("/operate/client-config.js");
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":true,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/operate\","
                + "\"organizationId\":null,"
                + "\"clusterId\":\"clusterId\","
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"isLoginDelegated\":false,"
                + "\"tasklistUrl\":\"https://tasklist.camunda.io/tl\","
                + "\"resourcePermissionsEnabled\":false,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }

  @Test
  public void testGetClientConfigForCantLogout() throws Exception {
    // given
    operateProperties.setTasklistUrl(null);
    given(operateProfileService.currentProfileCanLogout()).willReturn(false);
    // when
    final MockHttpServletRequestBuilder request = get("/operate/client-config.js");
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":false,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/operate\","
                + "\"organizationId\":null,"
                + "\"clusterId\":\"clusterId\","
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"isLoginDelegated\":false,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":false,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }

  @Test
  public void testGetClientConfigForNoTasklistURL() throws Exception {
    // given
    operateProperties.setTasklistUrl(null);
    given(operateProfileService.isDevelopmentProfileActive()).willReturn(false);

    // when
    final MockHttpServletRequestBuilder request = get("/operate/client-config.js");
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":false,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/operate\","
                + "\"organizationId\":null,"
                + "\"clusterId\":\"clusterId\","
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"isLoginDelegated\":false,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":false,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }
}
