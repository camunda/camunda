/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfig;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
import io.camunda.operate.webapp.security.OperateProfileService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"identity-auth", "test"})
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        OperateProfileService.class,
        ClientConfig.class,
        ClientConfigRestService.class,
        JacksonConfig.class,
        OperateProperties.class},
    properties = {
        OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"
    }
)
public class ClientConfigRestServiceIdentityTest extends OperateIntegrationTest {

  @Test
  public void testGetClientConfig() throws Exception {
    // when
    MockHttpServletRequestBuilder request = get("/client-config.js");
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/javascript"))
        .andReturn();

    // then
    assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(
        "window.clientConfig = {"
            + "\"isEnterprise\":false,"
            + "\"canLogout\":true,"
            + "\"contextPath\":\"\","
            + "\"organizationId\":null,"
            + "\"clusterId\":null,"
            + "\"mixpanelAPIHost\":null,"
            + "\"mixpanelToken\":null,"
            + "\"isLoginDelegated\":true,"
            + "\"tasklistUrl\":null,"
            + "\"resourcePermissionsEnabled\":false"
            + "};");
  }

}
