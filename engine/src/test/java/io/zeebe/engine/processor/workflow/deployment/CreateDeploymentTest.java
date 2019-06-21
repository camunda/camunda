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
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateDeploymentTest {
  private static final String PROCESS_ID = "process";
  private static final String PROCESS_ID_2 = "process2";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess(PROCESS_ID_2).startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_V2 =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent("v2").endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2_V2 =
      Bpmn.createExecutableProcess(PROCESS_ID_2).startEvent("v2").endEvent().done();

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
    assertThat(deployment.getKey()).isGreaterThanOrEqualTo(0L);

    Assertions.assertThat(deployment)
        .hasPartitionId(DEPLOYMENT_PARTITION)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(DeploymentIntent.DISTRIBUTED);
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
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("wf1.bpmn");

    deployedWorkflows = secondDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).hasSize(1);

    deployedWorkflow = deployedWorkflows.get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(PROCESS_ID);
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
        .contains(PROCESS_ID, PROCESS_ID_2);
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
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
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
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
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
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);
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
    Assertions.assertThat(rejectedDeployment.getRecordType())
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
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
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
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
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
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentIfNoResources() {
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
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
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
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
        ENGINE.deployment().withXmlResource("process1", modelInstance).deploy();
    final Record<DeploymentRecordValue> deployment2 =
        ENGINE.deployment().withXmlResource("process2", modelInstance).deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows().get(0).getVersion()).isEqualTo(1L);
    assertThat(deployment2.getValue().getDeployedWorkflows().get(0).getVersion()).isEqualTo(2L);
  }

  @Test
  public void shouldFilterDuplicateWorkflow() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertSameResource(originalWorkflows.get(0), repeatedWorkflows.get(0));
  }

  @Test
  public void shouldNotFilterWithDifferentResourceName() {
    // given
    final String originalResourceName = "process-1.bpmn";
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource(originalResourceName, WORKFLOW).deploy();

    // when
    final String repeatedResourceName = "process-2.bpmn";
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource(repeatedResourceName, WORKFLOW).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(originalWorkflows.get(0), repeatedWorkflows.get(0));
    assertThat(originalWorkflows.get(0).getResourceName()).isEqualTo(originalResourceName);
    assertThat(repeatedWorkflows.get(0).getResourceName()).isEqualTo(repeatedResourceName);
  }

  @Test
  public void shouldNotFilterWithDifferentResource() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW_V2).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(originalWorkflows.get(0), repeatedWorkflows.get(0));
  }

  @Test
  public void shouldFilterWithTwoEqualResources() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", WORKFLOW)
            .withXmlResource("p2.bpmn", WORKFLOW_2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", WORKFLOW)
            .withXmlResource("p2.bpmn", WORKFLOW_2)
            .deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isEqualTo(2);

    for (final DeployedWorkflow workflow : originalWorkflows) {
      assertSameResource(workflow, findWorkflow(repeatedWorkflows, workflow.getBpmnProcessId()));
    }
  }

  @Test
  public void shouldFilterWithOneDifferentAndOneEqual() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", WORKFLOW)
            .withXmlResource("p2.bpmn", WORKFLOW_2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", WORKFLOW)
            .withXmlResource("p2.bpmn", WORKFLOW_2_V2)
            .deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isEqualTo(2);

    assertSameResource(
        findWorkflow(originalWorkflows, PROCESS_ID), findWorkflow(repeatedWorkflows, PROCESS_ID));
    assertDifferentResources(
        findWorkflow(originalWorkflows, PROCESS_ID_2),
        findWorkflow(repeatedWorkflows, PROCESS_ID_2));
  }

  @Test
  public void shouldNotFilterWithRollbackToPreviousVersion() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("p1.bpmn", WORKFLOW).deploy();
    ENGINE.deployment().withXmlResource("p1.bpmn", WORKFLOW_V2).deploy();

    // when
    final Record<DeploymentRecordValue> rollback =
        ENGINE.deployment().withXmlResource("p1.bpmn", WORKFLOW).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = rollback.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(
        findWorkflow(originalWorkflows, PROCESS_ID), findWorkflow(repeatedWorkflows, PROCESS_ID));
  }

  @Test
  public void shouldFilterDuplicatesWithYamlResource() throws IOException, URISyntaxException {
    // given
    final Path yamlFile =
        Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final byte[] yamlModel = Files.readAllBytes(yamlFile);
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withYamlResource("process.yaml", yamlModel).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withYamlResource("process.yaml", yamlModel).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertSameResource(originalWorkflows.get(0), repeatedWorkflows.get(0));
  }

  @Test
  public void shouldNotFilterWithDifferentYamlResource() throws IOException, URISyntaxException {
    // given
    Path yamlFile = Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    byte[] yamlModel = Files.readAllBytes(yamlFile);
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withYamlResource("process.yaml", yamlModel).deploy();

    // when
    yamlFile = Paths.get(getClass().getResource("/workflows/other-workflow.yaml").toURI());
    yamlModel = Files.readAllBytes(yamlFile);
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withYamlResource("process.yaml", yamlModel).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(originalWorkflows.get(0), repeatedWorkflows.get(0));
  }

  private DeployedWorkflow findWorkflow(List<DeployedWorkflow> workflows, String processId) {
    return workflows.stream()
        .filter(w -> w.getBpmnProcessId().equals(processId))
        .findFirst()
        .orElse(null);
  }

  private void assertSameResource(
      final DeployedWorkflow original, final DeployedWorkflow repeated) {
    io.zeebe.protocol.record.Assertions.assertThat(repeated)
        .hasVersion(original.getVersion())
        .hasWorkflowKey(original.getWorkflowKey())
        .hasResourceName(original.getResourceName())
        .hasBpmnProcessId(original.getBpmnProcessId());
  }

  private void assertDifferentResources(
      final DeployedWorkflow original, final DeployedWorkflow repeated) {
    assertThat(original.getWorkflowKey()).isLessThan(repeated.getWorkflowKey());
    assertThat(original.getVersion()).isLessThan(repeated.getVersion());
  }
}
