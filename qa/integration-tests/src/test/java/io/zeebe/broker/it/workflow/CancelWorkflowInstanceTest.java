/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {
  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process")
          .startEvent("start")
          .serviceTask("task", t -> t.taskType("test"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init() {
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "workflow.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // when
    clientRule.getWorkflowClient().newCancelInstanceCommand(workflowInstance).send().join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CANCELED));
  }

  @Test
  public void shouldFailToCompleteJobAfterCancel() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    final List<JobEvent> jobEvents = new ArrayList<>();

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("test")
        .handler((c, job) -> jobEvents.add(job))
        .open();

    waitUntil(() -> jobEvents.size() > 0);

    clientRule.getWorkflowClient().newCancelInstanceCommand(workflowInstance).send().join();

    // when
    assertThatThrownBy(
            () -> {
              clientRule.getJobClient().newCompleteCommand(jobEvents.get(0)).send().join();
            })
        .isInstanceOf(ClientCommandRejectedException.class);

    // then
    waitUntil(() -> eventRecorder.hasJobEvent(JobState.CANCELED));
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CANCELED));
  }
}
