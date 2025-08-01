/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfig;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OperateProfileService.class,
      ClientConfig.class,
      ClientConfigRestService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperatePropertiesOverride.class,
      CamundaSecurityProperties.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    },
    properties = {
      OperateProperties.PREFIX + ".enterprise=true",
      OperateProperties.PREFIX + ".cloud.organizationid=organizationId",
      OperateProperties.PREFIX + ".cloud.mixpanelToken=i-am-a-token",
      OperateProperties.PREFIX + ".cloud.mixpanelAPIHost=https://fake.mixpanel.com",
      "camunda.security.multiTenancy.checksEnabled=true",
      "camunda.security.authorizations.enabled=false"
      // CAMUNDA_OPERATE_CLOUD_CLUSTERID=clusterId  -- leave out to test for null values
    })
public class ClientConfigRestServiceEnterpriseIT extends OperateAbstractIT {

  @Test
  public void testGetClientConfig() throws Exception {
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
                + "\"isEnterprise\":true,"
                + "\"canLogout\":true,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/operate\","
                + "\"organizationId\":\"organizationId\","
                + "\"clusterId\":null,"
                + "\"mixpanelAPIHost\":\"https://fake.mixpanel.com\","
                + "\"mixpanelToken\":\"i-am-a-token\","
                + "\"isLoginDelegated\":false,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":false,"
                + "\"multiTenancyEnabled\":true"
                + "};");
  }
}
