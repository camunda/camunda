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

import static io.zeebe.test.util.record.RecordingExporter.workflowInstanceRecords;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.util.TestEnvironmentRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.StringUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class UpdateWorkflowInstancePayloadTest {

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
  public void shouldCommandWithPayloadAsString() {
    // given
    final Workflow workflow = deployWorkflow();
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .workflowKey(workflow.getWorkflowKey())
        .send()
        .join()
        .getWorkflowInstanceKey();

    final Record<WorkflowInstanceRecordValue> taskActivatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task")
            .getFirst();

    final String updatedPayload = "{\"key\": \"val\"}";

    // when
    client
        .workflowClient()
        .newUpdatePayloadCommand(taskActivatedEvent.getKey())
        .payload(updatedPayload)
        .send()
        .join();

    // then
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        workflowInstanceRecords(WorkflowInstanceIntent.PAYLOAD_UPDATED).getFirst();
    JsonUtil.assertEquality(updatedEvent.getValue().getPayload(), updatedPayload);
  }

  @Test
  public void shouldCommandWithPayloadAsStream() {
    // given
    final Workflow workflow = deployWorkflow();
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .workflowKey(workflow.getWorkflowKey())
        .send()
        .join()
        .getWorkflowInstanceKey();

    final Record<WorkflowInstanceRecordValue> taskActivatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task")
            .getFirst();

    final String updatedPayload = "{\"key\": \"val\"}";
    final InputStream payloadStream = new ByteArrayInputStream(StringUtil.getBytes(updatedPayload));

    // when
    client
        .workflowClient()
        .newUpdatePayloadCommand(taskActivatedEvent.getKey())
        .payload(payloadStream)
        .send()
        .join();

    // then
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        workflowInstanceRecords(WorkflowInstanceIntent.PAYLOAD_UPDATED).getFirst();
    JsonUtil.assertEquality(updatedEvent.getValue().getPayload(), updatedPayload);
  }

  @Test
  public void shouldCommandWithPayloadAsMap() {
    // given
    final Workflow workflow = deployWorkflow();
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .workflowKey(workflow.getWorkflowKey())
        .send()
        .join()
        .getWorkflowInstanceKey();

    final Record<WorkflowInstanceRecordValue> taskActivatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task")
            .getFirst();

    final String updatedPayload = "{\"key\": \"val\"}";
    final Map<String, Object> payloadMap = Collections.<String, Object>singletonMap("key", "val");

    // when
    client
        .workflowClient()
        .newUpdatePayloadCommand(taskActivatedEvent.getKey())
        .payload(payloadMap)
        .send()
        .join();

    // then
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        workflowInstanceRecords(WorkflowInstanceIntent.PAYLOAD_UPDATED).getFirst();
    JsonUtil.assertEquality(updatedEvent.getValue().getPayload(), updatedPayload);
  }

  @Test
  public void shouldCommandWithPayloadAsObject() {
    // given
    final Workflow workflow = deployWorkflow();
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .workflowKey(workflow.getWorkflowKey())
        .send()
        .join()
        .getWorkflowInstanceKey();

    final Record<WorkflowInstanceRecordValue> taskActivatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task")
            .getFirst();

    final String updatedPayload = "{\"key\": \"val\"}";
    final Map<String, Object> payloadMap = Collections.<String, Object>singletonMap("key", "val");

    // when
    client
        .workflowClient()
        .newUpdatePayloadCommand(taskActivatedEvent.getKey())
        .payload((Object) payloadMap)
        .send()
        .join();

    // then
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        workflowInstanceRecords(WorkflowInstanceIntent.PAYLOAD_UPDATED).getFirst();
    JsonUtil.assertEquality(updatedEvent.getValue().getPayload(), updatedPayload);
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
