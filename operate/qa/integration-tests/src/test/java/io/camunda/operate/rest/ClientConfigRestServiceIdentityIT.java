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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles({"identity-auth", "test"})
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OperateProfileService.class,
      ClientConfig.class,
      ClientConfigRestService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class,
      SecurityConfiguration.class
    },
    properties = {OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"})
public class ClientConfigRestServiceIdentityIT extends OperateAbstractIT {

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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":true,"
                + "\"contextPath\":\"\","
                + "\"baseName\":\"/operate\","
                + "\"organizationId\":null,"
                + "\"clusterId\":null,"
                + "\"mixpanelAPIHost\":null,"
                + "\"mixpanelToken\":null,"
                + "\"isLoginDelegated\":true,"
                + "\"tasklistUrl\":null,"
                + "\"resourcePermissionsEnabled\":false,"
                + "\"multiTenancyEnabled\":false"
                + "};");
  }
}
