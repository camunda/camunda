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

import static io.zeebe.test.util.RecordingExporter.hasActivityEvent;
import static io.zeebe.test.util.RecordingExporter.hasJobEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
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
    waitUntil(() -> hasActivityEvent("process", WorkflowInstanceIntent.ELEMENT_TERMINATED));
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
    waitUntil(() -> hasJobEvent(JobIntent.CANCELED));
    waitUntil(() -> hasActivityEvent("process", WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }
}
