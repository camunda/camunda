/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import io.zeebe.protocol.record.intent.WorkflowIntent;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateDeploymentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String processId;
  private String processId2;
  private BpmnModelInstance workflow;
  private BpmnModelInstance workflow2;
  private BpmnModelInstance workflow_V2;
  private BpmnModelInstance workflow2_V2;

  private BpmnModelInstance createWorkflow(final String processId, final String startEventId) {
    return Bpmn.createExecutableProcess(processId).startEvent(startEventId).endEvent().done();
  }

  @Before
  public void init() {
    processId = Strings.newRandomValidBpmnId();
    processId2 = Strings.newRandomValidBpmnId();
    workflow = createWorkflow(processId, "v1");
    workflow2 = createWorkflow(processId2, "v1");
    workflow_V2 = createWorkflow(processId, "v2");
    workflow2_V2 = createWorkflow(processId2, "v2");
  }

  @Test
  public void shouldCreateDeploymentWithBpmnXml() {
    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(workflow).deploy();

    // then
    assertThat(deployment.getKey()).isNotNegative();

    Assertions.assertThat(deployment)
        .hasPartitionId(DEPLOYMENT_PARTITION)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void testLifecycle() {
    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(workflow).deploy();

    // then
    final var deploymentPartitionRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());

    assertThat(deploymentPartitionRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(WorkflowIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));
  }

  @Test
  public void testLifecycleOfDuplicateDeployment() {
    // when
    ENGINE.deployment().withXmlResource(workflow).deploy();
    final var duplicatedDeployment = ENGINE.deployment().withXmlResource(workflow).deploy();

    // then
    final var deploymentRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());

    assertThat(deploymentRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(WorkflowIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));

    final var duplicatedDeploymentRecords =
        RecordingExporter.records()
            .skipUntil(
                r ->
                    r.getIntent() == DeploymentIntent.CREATE
                        && r.getPosition() == duplicatedDeployment.getSourceRecordPosition())
            .limit(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());

    assertThat(duplicatedDeploymentRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));
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
        ENGINE.deployment().withXmlResource("wf1.bpmn", workflow).deploy();
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource("wf2.bpmn", workflow).deploy();

    // then
    List<DeployedWorkflow> deployedWorkflows = firstDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).hasSize(1);

    DeployedWorkflow deployedWorkflow = deployedWorkflows.get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("wf1.bpmn");

    deployedWorkflows = secondDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).hasSize(1);

    deployedWorkflow = deployedWorkflows.get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(processId);
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
            .withXmlResource("process.bpmn", workflow)
            .withXmlResource("process2.bpmn", workflow2)
            .deploy();

    // then
    assertThat(deployment.getValue().getDeployedWorkflows())
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains(processId, processId2);

    assertThat(deployment.getValue().getResources())
        .extracting(DeploymentResource::getResourceName)
        .contains("process.bpmn", "process2.bpmn");

    assertThat(deployment.getValue().getResources())
        .extracting(DeploymentResource::getResource)
        .contains(
            Bpmn.convertToString(workflow).getBytes(), Bpmn.convertToString(workflow2).getBytes());
  }

  @Test
  public void shouldWriteWorkflowRecordsOnDeployment() {
    // given

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", workflow)
            .withXmlResource("process2.bpmn", workflow2)
            .deploy()
            .getValue();

    // then
    final var workflowKeyList =
        deployment.getDeployedWorkflows().stream()
            .map(DeployedWorkflow::getWorkflowKey)
            .collect(Collectors.toList());

    final var workflowRecordKeys =
        RecordingExporter.workflowRecords()
            .limit(2)
            .map(Record::getKey)
            .collect(Collectors.toList());
    assertThat(workflowKeyList).hasSameElementsAs(workflowRecordKeys);

    final var firstWorkflowRecord =
        RecordingExporter.workflowRecords().withBpmnProcessId(processId).getFirst();
    assertThat(firstWorkflowRecord).isNotNull();
    assertThat(firstWorkflowRecord.getValue().getResourceName()).isEqualTo("process.bpmn");
    assertThat(firstWorkflowRecord.getValue().getVersion()).isEqualTo(1);
    assertThat(firstWorkflowRecord.getKey())
        .isEqualTo(firstWorkflowRecord.getValue().getWorkflowKey());

    final var secondWorkflowRecord =
        RecordingExporter.workflowRecords().withBpmnProcessId(processId2).getFirst();
    assertThat(secondWorkflowRecord).isNotNull();
    assertThat(secondWorkflowRecord.getValue().getResourceName()).isEqualTo("process2.bpmn");
    assertThat(secondWorkflowRecord.getValue().getVersion()).isEqualTo(1);
    assertThat(secondWorkflowRecord.getKey())
        .isEqualTo(secondWorkflowRecord.getValue().getWorkflowKey());
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
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
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
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldCreateDeploymentWithMultipleMessageStartEvent() {
    // given
    final ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess("processWithMultipleMsgStartEvent");
    processBuilder.startEvent().message(m -> m.name("startMessage1")).endEvent().done();
    final BpmnModelInstance process =
        processBuilder.startEvent().message(m -> m.name("startMessage2")).endEvent().done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldRejectDeploymentIfUsedInvalidMessage() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess().startEvent().intermediateCatchEvent("invalidMessage").done();

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
        .contains("ERROR: failed to parse expression");
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
        ENGINE.deployment().withXmlResource("process.bpmn", workflow).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", workflow).deploy();

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
        ENGINE.deployment().withXmlResource(originalResourceName, workflow).deploy();

    // when
    final String repeatedResourceName = "process-2.bpmn";
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource(repeatedResourceName, workflow).deploy();

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
        ENGINE.deployment().withXmlResource("process.bpmn", workflow).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", workflow_V2).deploy();

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
            .withXmlResource("p1.bpmn", workflow)
            .withXmlResource("p2.bpmn", workflow2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", workflow)
            .withXmlResource("p2.bpmn", workflow2)
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
            .withXmlResource("p1.bpmn", workflow)
            .withXmlResource("p2.bpmn", workflow2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", workflow)
            .withXmlResource("p2.bpmn", workflow2_V2)
            .deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isEqualTo(2);

    assertSameResource(
        findWorkflow(originalWorkflows, processId), findWorkflow(repeatedWorkflows, processId));
    assertDifferentResources(
        findWorkflow(originalWorkflows, processId2), findWorkflow(repeatedWorkflows, processId2));
  }

  @Test
  public void shouldNotFilterWithRollbackToPreviousVersion() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("p1.bpmn", workflow).deploy();
    ENGINE.deployment().withXmlResource("p1.bpmn", workflow_V2).deploy();

    // when
    final Record<DeploymentRecordValue> rollback =
        ENGINE.deployment().withXmlResource("p1.bpmn", workflow).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = rollback.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(
        findWorkflow(originalWorkflows, processId), findWorkflow(repeatedWorkflows, processId));
  }

  @Test
  public void shouldRejectDeploymentWithDuplicateResources() {
    // given
    final BpmnModelInstance definition1 =
        Bpmn.createExecutableProcess("process1").startEvent().done();
    final BpmnModelInstance definition2 =
        Bpmn.createExecutableProcess("process2").startEvent().done();
    final BpmnModelInstance definition3 =
        Bpmn.createExecutableProcess("process2")
            .startEvent()
            .serviceTask("task", (t) -> t.zeebeJobType("j").zeebeTaskHeader("k", "v"))
            .done();

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", definition1)
            .withXmlResource("p2.bpmn", definition2)
            .withXmlResource("p3.bpmn", definition3)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to deploy new resources, but encountered the following errors:\n"
                + "Duplicated process id in resources 'p2.bpmn' and 'p3.bpmn'");
  }

  @Test
  public void shouldRejectDeploymentWithInvalidTimerStartEventExpression() {
    // given
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess("process1")
            .startEvent("start-event-1")
            .timerWithCycleExpression("INVALID_CYCLE_EXPRESSION")
            .done();

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE.deployment().withXmlResource("p1.bpmn", definition).expectRejection().deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to deploy new resources, but encountered the following errors:\n"
                + "'p1.bpmn': - Element: start-event-1\n"
                + "    - ERROR: Invalid timer cycle expression ("
                + "failed to evaluate expression "
                + "'INVALID_CYCLE_EXPRESSION': no variable found for name "
                + "'INVALID_CYCLE_EXPRESSION')\n");
  }

  private DeployedWorkflow findWorkflow(
      final List<DeployedWorkflow> workflows, final String processId) {
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
