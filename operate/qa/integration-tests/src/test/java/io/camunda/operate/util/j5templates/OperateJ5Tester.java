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
package io.camunda.operate.util.j5templates;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.util.SearchTestRuleProvider;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This is a version of the OperateTester utility specifically for the JUnit 5 integration test
 * templates. This class is intended to be a work in progress and have additional functionality
 * ported over from OperateTester and other legacy test utilities as needed while refactoring
 * integration tests to the new templates
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateJ5Tester {

  private final ZeebeClient zeebeClient;

  @Autowired private SearchTestRuleProvider searchTestRuleProvider;

  @Autowired private SearchCheckPredicatesHolder searchPredicates;

  public OperateJ5Tester(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public Long deployProcessAndWait(String classpathResource) {
    Long processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, null, classpathResource);

    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessIsDeployedCheck(), processDefinitionKey);

    return processDefinitionKey;
  }

  public Long startProcessAndWait(String bpmnProcessId) {
    return startProcessAndWait(bpmnProcessId, null);
  }

  public Long startProcessAndWait(String bpmnProcessId, String payload) {
    Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstanceExistsCheck(), Arrays.asList(processInstanceKey));
    return processInstanceKey;
  }

  public void completeTaskAndWaitForProcessFinish(
      Long processInstanceKey, String activityId, String jobKey, String payload) {
    ZeebeTestUtil.completeTask(zeebeClient, jobKey, TestUtil.createRandomString(10), payload);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getFlowNodeIsCompletedCheck(), processInstanceKey, activityId);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstancesAreFinishedCheck(), Arrays.asList(processInstanceKey));
  }

  public void cancelProcessAndWait(Long processInstanceKey) {
    ZeebeTestUtil.cancelProcessInstance(zeebeClient, processInstanceKey);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstancesAreFinishedCheck(), Arrays.asList(processInstanceKey));
  }

  public void refreshSearchIndices() {
    searchTestRuleProvider.refreshSearchIndices();
  }
}
