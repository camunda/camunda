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

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfig;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.Before;
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
      OperatePropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      SecurityConfiguration.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
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
  @MockBean private SecurityConfiguration securityConfiguration;
  @MockBean private AuthorizationsConfiguration authorizationsConfiguration;
  @MockBean private AuthenticationConfiguration authenticationConfiguration;
  @MockBean private MultiTenancyConfiguration multiTenancyConfiguration;
  @MockBean private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

  @Autowired private OperateProperties operateProperties;

  @Before
  public void setUp() {
    // Mock the behavior of the security configuration
    given(securityConfiguration.getAuthentication()).willReturn(authenticationConfiguration);
    given(securityConfiguration.getAuthorizations()).willReturn(authorizationsConfiguration);
    given(securityConfiguration.getMultiTenancy()).willReturn(multiTenancyConfiguration);
    given(authenticationConfiguration.getOidc()).willReturn(oidcAuthenticationConfiguration);
    given(operateProfileService.isLoginDelegated()).willReturn(true);
  }

  @Test
  public void testGetClientConfig() throws Exception {
    // given
    operateProperties.setTasklistUrl("https://tasklist.camunda.io/tl");
    given(authorizationsConfiguration.isEnabled()).willReturn(true);
    given(oidcAuthenticationConfiguration.getOrganizationId()).willReturn(null);
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
                + "\"isLoginDelegated\":true,"
                + "\"tasklistUrl\":\"https://tasklist.camunda.io/tl\","
                + "\"resourcePermissionsEnabled\":true,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }

  @Test
  public void testGetClientConfigForCantLogout() throws Exception {
    // given
    operateProperties.setTasklistUrl(null);
    given(oidcAuthenticationConfiguration.getOrganizationId()).willReturn("test-org-id");
    given(authorizationsConfiguration.isEnabled()).willReturn(true);
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
                + "\"isLoginDelegated\":true,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":true,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }

  @Test
  public void testGetClientConfigForNoTasklistURL() throws Exception {
    // given
    operateProperties.setTasklistUrl(null);
    given(operateProfileService.isDevelopmentProfileActive()).willReturn(false);
    given(authorizationsConfiguration.isEnabled()).willReturn(true);
    given(oidcAuthenticationConfiguration.getOrganizationId()).willReturn("test-org-id");

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
                + "\"isLoginDelegated\":true,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":true,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }
}
