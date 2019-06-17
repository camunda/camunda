/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordMetadata;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateDeploymentTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateDeploymentWithBpmnXml() {
    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // then
    Assertions.assertThat(deployment.getKey()).isGreaterThanOrEqualTo(0L);

    final RecordMetadata metadata = deployment.getMetadata();
    Assertions.assertThat(metadata.getPartitionId()).isEqualTo(DEPLOYMENT_PARTITION);
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.EVENT);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
  }

  @Test
  public void shouldCreateDeploymentWithWorkflowWhichHaveUniqueKeys() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    final long workflowKey = deployment.getValue().getDeployedWorkflows().get(0).getWorkflowKey();
    final long deploymentKey = deployment.getKey();
    assertThat(workflowKey).isNotEqualTo(deploymentKey);
  }

  @Test
  public void shouldReturnDeployedWorkflowDefinitions() {
    // when
    final Record<DeploymentRecordValue> firstDeployment =
        ENGINE.deployment().withXmlResource("wf1.bpmn", WORKFLOW).deploy();
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource("wf2.bpmn", WORKFLOW).deploy();

    // then
    List<DeployedWorkflow> deployedWorkflows = firstDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).hasSize(1);

    DeployedWorkflow deployedWorkflow = deployedWorkflows.get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("wf1.bpmn");

    deployedWorkflows = secondDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).hasSize(1);

    deployedWorkflow = deployedWorkflows.get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("wf2.bpmn");
  }

  @Test
  public void shouldCreateDeploymentResourceWithCollaboration() {
    // given
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/workflows/collaboration.bpmn");
    final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(resourceAsStream);

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource("collaboration.bpmn", modelInstance).deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows())
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains("process1", "process2");
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", WORKFLOW)
            .withXmlResource("process2.bpmn", WORKFLOW_2)
            .deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows())
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains("process", "process2");
  }

  @Test
  public void shouldCreateDeploymentIfUnusedInvalidMessage() {
    // given
    final BpmnModelInstance process = Bpmn.createExecutableProcess().startEvent().done();
    process.getDefinitions().addChildElement(process.newInstance(Message.class));

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deployment.getMetadata().getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
  }

  @Test
  public void shouldCreateDeploymentWithMessageStartEvent() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();
    final BpmnModelInstance process =
        processBuilder.startEvent().message(m -> m.name("startMessage")).endEvent().done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deployment.getMetadata().getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
  }

  @Test
  public void shouldCreateDeploymentWithMultipleMessageStartEvent() {
    // given
    final ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess("processWithMulitpleMsgStartEvent");
    processBuilder.startEvent().message(m -> m.name("startMessage1")).endEvent().done();
    final BpmnModelInstance process =
        processBuilder.startEvent().message(m -> m.name("startMessage2")).endEvent().done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deployment.getMetadata().getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
  }

  @Test
  public void shouldRejectDeploymentIfUsedInvalidMessage() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess().startEvent().intermediateCatchEvent("invalidmessage").done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getMetadata().getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldRejectDeploymentIfNotValidDesignTimeAspect() throws Exception {
    // given
    final Path path = Paths.get(getClass().getResource("/workflows/invalid_process.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getKey())
        .isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());

    final RecordMetadata metadata = rejectedDeployment.getMetadata();
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    Assertions.assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    Assertions.assertThat(metadata.getRejectionReason())
        .contains("ERROR: Must have at least one start event");
  }

  @Test
  public void shouldRejectDeploymentIfNotValidRuntimeAspect() throws Exception {
    // given
    final Path path =
        Paths.get(getClass().getResource("/workflows/invalid_process_condition.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getKey())
        .isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());

    final RecordMetadata metadata = rejectedDeployment.getMetadata();
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    Assertions.assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    Assertions.assertThat(metadata.getRejectionReason())
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

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource(resource1)
            .withXmlResource(resource2)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getKey())
        .isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());

    final RecordMetadata metadata = rejectedDeployment.getMetadata();
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    Assertions.assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentIfNoResources() {
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getKey())
        .isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());

    final RecordMetadata metadata = rejectedDeployment.getMetadata();
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    Assertions.assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentIfNotParsable() {
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource("not a workflow".getBytes(UTF_8))
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getKey())
        .isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());

    final RecordMetadata metadata = rejectedDeployment.getMetadata();
    Assertions.assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    Assertions.assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    Assertions.assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldCreateDeploymentWithYamlWorfklow() throws Exception {
    // given
    final Path yamlFile =
        Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final byte[] yamlWorkflow = Files.readAllBytes(yamlFile);

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withYamlResource(yamlWorkflow).deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows())
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains("yaml-workflow");
  }

  @Test
  public void shouldIncrementWorkflowVersions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldIncrementWorkflowVersions")
            .startEvent()
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final Record<DeploymentRecordValue> deployment2 =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows().get(0).getVersion()).isEqualTo(1L);
    assertThat(deployment2.getValue().getDeployedWorkflows().get(0).getVersion()).isEqualTo(2L);
  }
}
