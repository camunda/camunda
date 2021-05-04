/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
import org.junit.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.annotation.PrepareTestInstance;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    classes = {TestApplicationWithNoBeans.class, ClientConfigRestService.class, JacksonConfig.class, OperateProperties.class}
)
public class ClientConfigRestServiceTest extends OperateIntegrationTest {

  @Test
  public void testGetClientConfig() throws Exception {
    // when
    MockHttpServletRequestBuilder request = get("/client-config.js");
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/javascript"))
        .andReturn();

    // then
    assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("window.clientConfig = { \"isEnterprise\": false, \"contextPath\": \"\" };");
  }

}
