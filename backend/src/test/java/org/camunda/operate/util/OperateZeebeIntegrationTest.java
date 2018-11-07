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

import java.util.function.Predicate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.EmbeddedBrokerRule;
import static org.assertj.core.api.Assertions.fail;

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

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  @Qualifier("workflowIsDeployedCheck")
  private Predicate<Object[]> workflowIsDeployedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  private JobWorker jobWorker;

  private String workerName;

  protected void before() {
    workerName = TestUtil.createRandomString(10);
    operateProperties.getZeebe().setWorker(workerName);
    operateProperties.getZeebeElasticsearch().setPrefix(brokerRule.getPrefix());
    try {
      FieldSetter.setField(zeebeESImporter, ZeebeESImporter.class.getDeclaredField("zeebeClient"), getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  protected void after() {
    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }
  }

  public OperateZeebeIntegrationTest() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE);
  }

  public OperateZeebeIntegrationTest(
    final String configFileClasspathLocation) {
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

  public Long failTaskWithNoRetriesLeft(String taskName, String workflowInstanceId) {
    Long jobKey = ZeebeTestUtil.failTask(getClient(), taskName, getWorkerName(), 3);
    elasticsearchTestRule.processAllEventsAndWait(incidentIsActiveCheck, workflowInstanceId);
    return jobKey;
  }

  protected String deployWorkflow(String... classpathResources) {
    final String workflowId = ZeebeTestUtil.deployWorkflow(getClient(), classpathResources);
    elasticsearchTestRule.processAllEventsAndWait(workflowIsDeployedCheck, workflowId);
    return workflowId;
  }

  protected String deployWorkflow(BpmnModelInstance workflow, String resourceName) {
    final String workflowId = ZeebeTestUtil.deployWorkflow(getClient(), workflow, resourceName);
    elasticsearchTestRule.processAllEventsAndWait(workflowIsDeployedCheck, workflowId);
    return workflowId;
  }

  protected void cancelWorkflowInstance(String workflowInstanceId) {
    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceId);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceId);
  }

  protected void completeTask(String workflowInstanceId, String activityId, String payload) {
    JobWorker jobWorker = ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), payload);
    elasticsearchTestRule.processAllEventsAndWait(activityIsCompletedCheck, workflowInstanceId, activityId);
    jobWorker.close();
  }
}
