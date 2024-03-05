/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.ClientConfig;
import io.camunda.operate.webapp.rest.ClientConfigRestService;
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
      OperateProperties.class
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":true,"
                + "\"contextPath\":\"\","
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":false,"
                + "\"contextPath\":\"\","
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
                + "\"isEnterprise\":false,"
                + "\"canLogout\":false,"
                + "\"contextPath\":\"\","
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
