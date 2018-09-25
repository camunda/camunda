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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.base.Charsets;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.response.CreateWorkflowInstanceResponse;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.TestEnvironmentRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CreateWorkflowInstanceTest {

  @Rule public TestEnvironmentRule rule = new TestEnvironmentRule();

  private ZeebeClient client;
  public static final BpmnModelInstance TEST_WORKFLOW =
      Bpmn.createExecutableProcess("testProcess").startEvent().endEvent().done();

  @Before
  public void setUp() {
    client = rule.getClient();
  }

  @Test
  public void shouldCreateWorkflowInstanceByWorkflowInstanceKey() {
    // given
    deployWorkflow();
    final Workflow workflow = deployWorkflow();
    deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .send()
            .join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(workflow.getWorkflowKey());
    assertThat(response.getBpmnProcessId()).isEqualTo(workflow.getBpmnProcessId());
    assertThat(response.getVersion()).isEqualTo(workflow.getVersion());

    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord).hasKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getMetadata()).hasPartitionId(response.getPartitionId());
    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasBpmnProcessId(response.getBpmnProcessId())
        .hasVersion(response.getVersion())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessId() {
    // given
    deployWorkflow();
    deployWorkflow();
    final Workflow workflow = deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(workflow.getBpmnProcessId())
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(workflow.getWorkflowKey());
    assertThat(response.getBpmnProcessId()).isEqualTo(workflow.getBpmnProcessId());
    assertThat(response.getVersion()).isEqualTo(workflow.getVersion());

    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord).hasKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getMetadata()).hasPartitionId(response.getPartitionId());
    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasBpmnProcessId(response.getBpmnProcessId())
        .hasVersion(response.getVersion())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessIdAndVersion() {
    // given
    deployWorkflow();
    final Workflow workflow = deployWorkflow();
    deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(workflow.getBpmnProcessId())
            .version(workflow.getVersion())
            .send()
            .join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(workflow.getWorkflowKey());
    assertThat(response.getBpmnProcessId()).isEqualTo(workflow.getBpmnProcessId());
    assertThat(response.getVersion()).isEqualTo(workflow.getVersion());

    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord).hasKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getMetadata()).hasPartitionId(response.getPartitionId());
    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasBpmnProcessId(response.getBpmnProcessId())
        .hasVersion(response.getVersion())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldCreateWorkflowInstanceWithStringPayload() {
    // given
    final Workflow workflow = deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .payload("{\"foo\": \"bar\"}")
            .send()
            .join();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap())
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithInputStreamPayload() {
    // given
    final Workflow workflow = deployWorkflow();
    final String payload = "{\"foo\": \"bar\"}";
    final InputStream inputStream = new ByteArrayInputStream(payload.getBytes(Charsets.UTF_8));

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .payload(inputStream)
            .send()
            .join();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap())
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithMapPayload() {
    // given
    final Workflow workflow = deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap())
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithObjectPayload() {
    // given
    final Workflow workflow = deployWorkflow();

    // when
    final CreateWorkflowInstanceResponse response =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflow.getWorkflowKey())
            .payload(new Payload())
            .send()
            .join();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecord =
        workflowInstanceRecords(WorkflowInstanceIntent.CREATED).getFirst();

    assertThat(workflowInstanceRecord.getValue())
        .hasWorkflowKey(response.getWorkflowKey())
        .hasWorkflowInstanceKey(response.getWorkflowInstanceKey());
    assertThat(workflowInstanceRecord.getValue().getPayloadAsMap())
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldNotWorkflowInstanceWithNoJsonObjectAsPayload() {
    // given
    final Workflow workflow = deployWorkflow();

    // when
    assertThatThrownBy(
            () ->
                client
                    .workflowClient()
                    .newCreateInstanceCommand()
                    .workflowKey(workflow.getWorkflowKey())
                    .payload("[]")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldNotWorkflowInstanceWithUnknownWorkflowKey() {
    // when
    assertThatThrownBy(
            () -> client.workflowClient().newCreateInstanceCommand().workflowKey(123).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Workflow is not deployed");
  }

  @Test
  public void shouldNotWorkflowInstanceWithUnknownBpmnProcessId() {
    // when
    assertThatThrownBy(
            () ->
                client
                    .workflowClient()
                    .newCreateInstanceCommand()
                    .bpmnProcessId("unknown")
                    .latestVersion()
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Workflow is not deployed");
  }

  @Test
  public void shouldNotWorkflowInstanceWithUnknownVersion() {
    // given
    final Workflow workflow = deployWorkflow();

    // when
    assertThatThrownBy(
            () ->
                client
                    .workflowClient()
                    .newCreateInstanceCommand()
                    .bpmnProcessId(workflow.getBpmnProcessId())
                    .version(workflow.getVersion() + 1)
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Workflow is not deployed");
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

  public static class Payload {

    private final String foo = "bar";

    Payload() {}

    public String getFoo() {
      return foo;
    }
  }
}
