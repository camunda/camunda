/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.it;

import org.assertj.core.api.Assertions;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.HealthCheckRestService;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.OperateZeebeBrokerRule;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeClientRule;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import io.zeebe.test.EmbeddedBrokerRule;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ZeebeConnectorIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private OperateProperties operateProperties;

  private OperateZeebeBrokerRule brokerRule;

  private ZeebeClientRule clientRule;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @After
  public void cleanup() {
    if (brokerRule != null) {
      brokerRule.after();
    }
    if (clientRule != null) {
      clientRule.after();
    }
  }

  @Test
  public void testZeebeConnection() throws Exception {
    //when 1
    //no Zeebe broker is running

    //then 2
    //application context must be successfully started
    MockHttpServletRequestBuilder request = get(HealthCheckRestService.HEALTH_CHECK_URL);
    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andReturn();
    try {
      zeebeESImporter.processNextEntitiesBatch();
      fail("Exception is expected");
    } catch (Exception ex) {
      //expected exception
    }

    //when 2
    //Zeebe is started
    brokerRule = new OperateZeebeBrokerRule(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE);
    clientRule = new ZeebeClientRule(brokerRule);
    brokerRule.before();
    clientRule.before();

    String workerName = TestUtil.createRandomString(10);
    operateProperties.getZeebe().setWorker(workerName);
    operateProperties.getZeebeElasticsearch().setPrefix(brokerRule.getPrefix());
    try {
      FieldSetter.setField(zeebeESImporter, ZeebeESImporter.class.getDeclaredField("zeebeClient"), clientRule.getClient());
    } catch (NoSuchFieldException e) {
      Assertions.fail("Failed to inject ZeebeClient into some of the beans");
    }

    //then 2
    //data import is working
    zeebeESImporter.processNextEntitiesBatch();

  }

}
