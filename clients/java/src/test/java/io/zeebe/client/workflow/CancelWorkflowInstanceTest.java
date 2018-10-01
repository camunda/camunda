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
package io.zeebe.client.workflow;

import static io.zeebe.exporter.record.Assertions.assertThat;
import static io.zeebe.test.util.record.RecordingExporter.workflowInstanceRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.util.TestEnvironmentRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CancelWorkflowInstanceTest {

  public static final BpmnModelInstance TEST_WORKFLOW =
      Bpmn.createExecutableProcess("testProcess")
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("taskType"))
          .endEvent()
          .done();

  @Rule public TestEnvironmentRule rule = new TestEnvironmentRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = rule.getClient();
  }

  @Test
  public void shouldSendCancelCommand() {
    // given
    final Workflow workflow = deployWorkflow();
    final long workflowInstanceKey =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .send()
            .join()
            .getWorkflowInstanceKey();

    // when
    client.workflowClient().newCancelInstanceCommand(workflowInstanceKey).send().join();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CANCELING).getFirst();
    assertThat(workflowInstanceRecord.getValue()).hasWorkflowInstanceKey(workflowInstanceKey);
  }

  private Workflow deployWorkflow() {
    return client
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(TEST_WORKFLOW, "testProcess.bpmn")
        .send()
        .join()
        .getDeployedWorkflows()
        .get(0);
  }
}
