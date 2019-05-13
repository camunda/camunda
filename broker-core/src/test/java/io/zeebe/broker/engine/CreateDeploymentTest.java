/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.engine;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.util.StreamUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  private static final long FIRST_WORKFLOW_KEY =
      Protocol.encodePartitionId(DEPLOYMENT_PARTITION, 1);

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldCreateDeploymentWithBpmnXml() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put(
                "resources",
                Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getKey()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.getPartitionId()).isEqualTo(DEPLOYMENT_PARTITION);

    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldCreateDeploymentWithWorkflowWhichHaveUniqueKeys() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    final ExecuteCommandResponse response = apiRule.partitionClient().deployWithResponse(process);

    // then
    final long workflowKey =
        (long)
            ((ArrayList<Map<String, Object>>) response.getValue().get("workflows"))
                .get(0)
                .get("workflowKey");

    final long deploymentKey = response.getKey();
    assertThat(workflowKey).isNotEqualTo(deploymentKey);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnDeployedWorkflowDefinitions() {
    // when
    final ExecuteCommandResponse firstDeployment =
        apiRule.partitionClient().deployWithResponse(WORKFLOW, "wf1.bpmn");
    final ExecuteCommandResponse secondDeployment =
        apiRule.partitionClient().deployWithResponse(WORKFLOW, "wf2.bpmn");

    // then
    List<Map<String, Object>> deployedWorkflows =
        (List<Map<String, Object>>) firstDeployment.getValue().get("workflows");
    assertThat(deployedWorkflows).hasSize(1);
    assertThat(deployedWorkflows.get(0))
        .containsExactly(
            entry("bpmnProcessId", "process"),
            entry("version", 1L),
            entry("workflowKey", FIRST_WORKFLOW_KEY),
            entry("resourceName", "wf1.bpmn"));

    deployedWorkflows = (List<Map<String, Object>>) secondDeployment.getValue().get("workflows");
    assertThat(deployedWorkflows).hasSize(1);
    assertThat(deployedWorkflows.get(0))
        .containsExactly(
            entry("bpmnProcessId", "process"),
            entry("version", 2L),
            entry("workflowKey", FIRST_WORKFLOW_KEY + 2),
            entry("resourceName", "wf2.bpmn"));
  }

  @Test
  public void shouldCreateDeploymentResourceWithCollaboration() throws IOException {
    // given
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/workflows/collaboration.bpmn");

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .partitionClient()
            .deployWithResponse(
                StreamUtil.read(resourceAsStream),
                ResourceType.BPMN_XML.name(),
                "collaboration.bpmn");

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final List<Map<String, Object>> deployedWorkflows =
        Arrays.asList(getDeployedWorkflow(resp, 0), getDeployedWorkflow(resp, 1));

    assertThat(deployedWorkflows)
        .extracting(s -> s.get(WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID))
        .contains("process1", "process2");
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() {
    // given
    final List<Map<String, Object>> resources = new ArrayList<>();
    resources.add(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn"));
    resources.add(deploymentResource(bpmnXml(WORKFLOW_2), "process2.bpmn"));

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("resources", resources)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final List<Map<String, Object>> deployedWorkflows =
        Arrays.asList(getDeployedWorkflow(resp, 0), getDeployedWorkflow(resp, 1));

    assertThat(deployedWorkflows)
        .extracting(s -> s.get(WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID))
        .contains("process", "process2");
  }

  @Test
  public void shouldCreateDeploymentIfUnusedInvalidMessage() throws IOException {
    // given
    final BpmnModelInstance process = Bpmn.createExecutableProcess().startEvent().done();
    process.getDefinitions().addChildElement(process.newInstance(Message.class));

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(process);

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldCreateDeploymentWithMessageStartEvent() throws IOException {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();
    final BpmnModelInstance process =
        processBuilder.startEvent().message(m -> m.name("startMessage")).endEvent().done();

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(process);

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldCreateDeploymentWithMultipleMessageStartEvent() throws IOException {
    // given
    final ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess("processWithMulitpleMsgStartEvent");
    processBuilder.startEvent().message(m -> m.name("startMessage1")).endEvent().done();
    final BpmnModelInstance process =
        processBuilder.startEvent().message(m -> m.name("startMessage2")).endEvent().done();

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(process);

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldRejectDeploymentIfUsedInvalidMessage() throws IOException {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess().startEvent().intermediateCatchEvent("invalidmessage").done();

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(process);

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldRejectDeploymentIfNotValidDesignTimeAspect() throws Exception {
    // given
    final Path path = Paths.get(getClass().getResource("/workflows/invalid_process.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(resource);

    // then
    assertThat(resp.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(resp.getRejectionReason()).contains("ERROR: Must have at least one start event");
  }

  @Test
  public void shouldRejectDeploymentIfNotValidRuntimeAspect() throws Exception {
    // given
    final Path path =
        Paths.get(getClass().getResource("/workflows/invalid_process_condition.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final ExecuteCommandResponse resp = apiRule.partitionClient().deployWithResponse(resource);

    // then
    assertThat(resp.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(resp.getRejectionReason())
        .contains("Element: flow2 > conditionExpression")
        .contains("ERROR: Condition expression is invalid");
  }

  @Test
  public void shouldRejectDeploymentIfOneResourceIsNotValid() throws Exception {
    // given
    final Path path1 = Paths.get(getClass().getResource("/workflows/invalid_process.bpmn").toURI());
    final Path path2 = Paths.get(getClass().getResource("/workflows/collaboration.bpmn").toURI());
    final byte[] resource1 = Files.readAllBytes(path1);
    final byte[] resource2 = Files.readAllBytes(path2);

    final List<Map<String, Object>> resources =
        Arrays.asList(
            deploymentResource(resource1, "process1.bpmn"),
            deploymentResource(resource2, "process2.bpmn"));

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("resources", resources)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATE);
  }

  @Test
  public void shouldRejectDeploymentIfNoResources() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("resources", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentIfNotParsable() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .partitionClient()
            .deployWithResponse(
                "not a workflow".getBytes(UTF_8), ResourceType.BPMN_XML.name(), "invalid.bpmn");

    // then
    assertThat(resp.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldCreateDeploymentWithYamlWorfklow() throws Exception {
    // given
    final Path yamlFile =
        Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final byte[] yamlWorkflow = Files.readAllBytes(yamlFile);

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .partitionClient()
            .deployWithResponse(
                yamlWorkflow, ResourceType.YAML_WORKFLOW.name(), "simple-workflow.yaml");

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(resp, 0);

    assertThat(deployedWorkflow)
        .containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow");
  }

  @Test
  public void shouldIncrementWorkflowVersions() {
    // given

    // when
    final ExecuteCommandResponse d1 = apiRule.partitionClient().deployWithResponse(WORKFLOW);
    final ExecuteCommandResponse d2 = apiRule.partitionClient().deployWithResponse(WORKFLOW);

    // then
    final Map<String, Object> workflow1 = getDeployedWorkflow(d1, 0);
    assertThat(workflow1.get("version")).isEqualTo(1L);

    final Map<String, Object> workflow2 = getDeployedWorkflow(d2, 0);
    assertThat(workflow2.get("version")).isEqualTo(2L);
  }

  private Map<String, Object> deploymentResource(final byte[] resource, final String name) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", ResourceType.BPMN_XML);
    deploymentResource.put("resourceName", name);

    return deploymentResource;
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getDeployedWorkflow(
      final ExecuteCommandResponse d1, final int offset) {
    final List<Map<String, Object>> d1Workflows =
        (List<Map<String, Object>>) d1.getValue().get("workflows");
    return d1Workflows.get(offset);
  }

  public Record<DeploymentRecordValue> getFirstDeploymentCreateCommand() {
    return apiRule
        .partitionClient(DEPLOYMENT_PARTITION)
        .receiveDeployments()
        .withIntent(DeploymentIntent.CREATE)
        .getFirst();
  }
}
