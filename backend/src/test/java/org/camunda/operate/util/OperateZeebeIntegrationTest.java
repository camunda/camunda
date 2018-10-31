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
package org.camunda.operate.util;

import java.util.Properties;
import java.util.function.Supplier;
import org.camunda.operate.property.OperateProperties;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.test.EmbeddedBrokerRule;

public abstract class OperateZeebeIntegrationTest extends OperateIntegrationTest {

  @MockBean
  protected ZeebeClient mockedZeebeClient;    //we don't want to create ZeebeClient, we will rather use the one from test rule

  public final OperateZeebeBrokerRule brokerRule;

  public final ZeebeClientRule clientRule;

  @Rule
  public RuleChain chain;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  protected OperateProperties operateProperties;

  private JobWorker jobWorker;

  private String workerName;

  protected void before() {
    workerName = TestUtil.createRandomString(10);
    operateProperties.getZeebe().setWorker(workerName);

    operateProperties.getZeebeElasticsearch().setPrefix(brokerRule.getPrefix());

    try {
      //wait till topic is created
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      //
    }
  }

  protected void after() {
    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }

  }

  public OperateZeebeIntegrationTest() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE, Properties::new);
  }

  public OperateZeebeIntegrationTest(
    final String configFileClasspathLocation, final Supplier<Properties> clientPropertiesProvider) {
    brokerRule = new OperateZeebeBrokerRule(configFileClasspathLocation);

    clientRule = new ZeebeClientRule(brokerRule);

    chain = RuleChain.outerRule(brokerRule).around(clientRule);
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public String getWorkerName() {
    return workerName;
  }

  public JobWorker getJobWorker() {
    return jobWorker;
  }

  public void setJobWorker(JobWorker jobWorker) {
    this.jobWorker = jobWorker;
  }

  public void failTaskWithNoRetriesLeft(String taskName) {
    setJobWorker(ZeebeUtil.failTask(getClient(), taskName, getWorkerName(), 3));
    elasticsearchTestRule.processAllEvents(20);
    getJobWorker().close();
    setJobWorker(null);
  }

}
