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
package io.zeebe.broker.workflow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.util.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest {
  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process").startEvent().endEvent().done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldCreateDeploymentWithBpmnXml() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
            .put(
                "resources",
                Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
            .done()
            .sendAndAwait();

    // then
    final SubscribedRecord createDeploymentCommand = getFirstDeploymentCreateCommand();

    assertThat(resp.key()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.sourceRecordPosition()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnDeployedWorkflowDefinitions() {
    // when
    final ExecuteCommandResponse firstDeployment =
        apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW, "wf1.bpmn");
    final ExecuteCommandResponse secondDeployment =
        apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW, "wf2.bpmn");

    // then
    List<Map<String, Object>> deployedWorkflows =
        (List<Map<String, Object>>) firstDeployment.getValue().get("deployedWorkflows");
    assertThat(deployedWorkflows).hasSize(1);
    assertThat(deployedWorkflows.get(0))
        .containsExactly(
            entry("bpmnProcessId", "process"),
            entry("version", 1),
            entry("workflowKey", 1),
            entry("resourceName", "wf1.bpmn"));

    deployedWorkflows =
        (List<Map<String, Object>>) secondDeployment.getValue().get("deployedWorkflows");
    assertThat(deployedWorkflows).hasSize(1);
    assertThat(deployedWorkflows.get(0))
        .containsExactly(
            entry("bpmnProcessId", "process"),
            entry("version", 2),
            entry("workflowKey", 2),
            entry("resourceName", "wf2.bpmn"));
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() throws IOException {
    // given
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/workflows/collaboration.bpmn");

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .topic()
            .deployWithResponse(
                ClientApiRule.DEFAULT_TOPIC_NAME,
                StreamUtil.read(resourceAsStream),
                ResourceType.BPMN_XML.name(),
                "collaboration.bpmn");

    // then
    assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATED);

    final List<Map<String, Object>> deployedWorkflows =
        Arrays.asList(getDeployedWorkflow(resp, 0), getDeployedWorkflow(resp, 1));

    assertThat(deployedWorkflows)
        .extracting(s -> s.get(WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID))
        .contains("process1", "process2");
  }

  @Test
  public void shouldRejectDeploymentIfTopicNotExists() {
    // when
    final ExecuteCommandResponse resp =
        apiRule.topic().deployWithResponse("not-existing", WORKFLOW);

    // then
    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason()).isEqualTo("Topic does not exist");
  }

  @Test
  public void shouldRejectDeploymentIfNotValid() throws Exception {
    // given
    final Path path = Paths.get(getClass().getResource("/workflows/invalid_process.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final ExecuteCommandResponse resp =
        apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, resource);

    // then
    final SubscribedRecord createDeploymentCommand = getFirstDeploymentCreateCommand();

    assertThat(resp.key()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.sourceRecordPosition()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason())
        .contains("The process must contain at least one none start event.");
  }

  @Test
  public void shouldRejectDeploymentIfOneResourceIsNotValid() throws Exception {
    // given
    final Path path = Paths.get(getClass().getResource("/workflows/invalid_process.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    final List<Map<String, Object>> resources =
        Arrays.asList(deploymentResource(resource, "process2.bpmn"));

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
            .put("resources", resources)
            .done()
            .sendAndAwait();

    // then
    final SubscribedRecord createDeploymentCommand = getFirstDeploymentCreateCommand();

    assertThat(resp.key()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.sourceRecordPosition()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason())
        .contains("Resource 'process2.bpmn':")
        .contains("The process must contain at least one none start event.");
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATE);
  }

  @Test
  public void shouldRejectDeploymentIfNoResources() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
            .put("resources", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    final SubscribedRecord createDeploymentCommand = getFirstDeploymentCreateCommand();

    assertThat(resp.key()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.sourceRecordPosition()).isEqualTo(createDeploymentCommand.position());
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason()).isEqualTo("Deployment doesn't contain a resource to deploy");
  }

  @Test
  public void shouldRejectDeploymentIfNotParsable() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .topic()
            .deployWithResponse(
                ClientApiRule.DEFAULT_TOPIC_NAME,
                "not a workflow".getBytes(UTF_8),
                ResourceType.BPMN_XML.name(),
                "invalid.bpmn");

    // then
    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason())
        .contains("Failed to deploy resource 'invalid.bpmn':")
        .contains("Failed to read BPMN model");
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
            .topic()
            .deployWithResponse(
                ClientApiRule.DEFAULT_TOPIC_NAME,
                yamlWorkflow,
                ResourceType.YAML_WORKFLOW.name(),
                "simple-workflow.yaml");

    // then
    assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATED);

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(resp, 0);

    assertThat(deployedWorkflow)
        .containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow");
  }

  @Test
  public void shouldAssignWorkflowVersionsPerTopic() {
    // given
    apiRule.createTopic("foo", 1);
    apiRule.createTopic("bar", 1);

    // when
    final ExecuteCommandResponse d1 = apiRule.topic().deployWithResponse("foo", WORKFLOW);
    final ExecuteCommandResponse d2 = apiRule.topic().deployWithResponse("bar", WORKFLOW);

    // then
    final Map<String, Object> workflow1 = getDeployedWorkflow(d1, 0);
    assertThat(workflow1.get("version")).isEqualTo(1);

    final Map<String, Object> workflow2 = getDeployedWorkflow(d2, 0);
    assertThat(workflow2.get("version")).isEqualTo(1);
  }

  private Map<String, Object> deploymentResource(final byte[] resource, String name) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", ResourceType.BPMN_XML);
    deploymentResource.put("resourceName", name);

    return deploymentResource;
  }

  private byte[] bpmnXml(final WorkflowDefinition definition) {
    return Bpmn.convertToString(definition).getBytes(UTF_8);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getDeployedWorkflow(final ExecuteCommandResponse d1, int offset) {
    final List<Map<String, Object>> d1Workflows =
        (List<Map<String, Object>>) d1.getValue().get("deployedWorkflows");
    return d1Workflows.get(offset);
  }

  public SubscribedRecord getFirstDeploymentCreateCommand() {
    return apiRule
        .topic(Protocol.SYSTEM_PARTITION)
        .receiveRecords()
        .skipUntil(r -> r.valueType() == ValueType.DEPLOYMENT)
        .filter(r -> r.valueType() == ValueType.DEPLOYMENT && r.intent() == DeploymentIntent.CREATE)
        .findFirst()
        .get();
  }
}
