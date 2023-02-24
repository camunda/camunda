/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.InputStream;
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
  private BpmnModelInstance process;
  private BpmnModelInstance process2;
  private BpmnModelInstance process_V2;
  private BpmnModelInstance process2_V2;

  private BpmnModelInstance createProcess(final String processId, final String startEventId) {
    return Bpmn.createExecutableProcess(processId).startEvent(startEventId).endEvent().done();
  }

  @Before
  public void init() {
    processId = Strings.newRandomValidBpmnId();
    processId2 = Strings.newRandomValidBpmnId();
    process = createProcess(processId, "v1");
    process2 = createProcess(processId2, "v1");
    process_V2 = createProcess(processId, "v2");
    process2_V2 = createProcess(processId2, "v2");
  }

  @Test
  public void shouldCreateDeploymentWithBpmnXml() {
    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

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
    ENGINE.deployment().withXmlResource(process).deploy();

    // then
    final var deploymentPartitionRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());

    assertThat(deploymentPartitionRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));
  }

  @Test
  public void testLifecycleOfDuplicateDeployment() {
    // when
    ENGINE.deployment().withXmlResource(process).deploy();
    final var duplicatedDeployment = ENGINE.deployment().withXmlResource(process).deploy();

    // then
    final var deploymentRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());

    assertThat(deploymentRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
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
  public void shouldCreateDeploymentWithProcessWhichHaveUniqueKeys() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    final long deploymentKey = deployment.getKey();
    assertThat(processDefinitionKey).isNotEqualTo(deploymentKey);
  }

  @Test
  public void shouldReturnDeployedProcessDefinitions() {
    // when
    final Record<DeploymentRecordValue> firstDeployment =
        ENGINE.deployment().withXmlResource("wf1.bpmn", process).deploy();
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource("wf2.bpmn", process).deploy();

    // then
    var deployedProcesses = firstDeployment.getValue().getProcessesMetadata();
    assertThat(deployedProcesses).hasSize(1);

    ProcessMetadataValue deployedProcess = deployedProcesses.get(0);
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedProcess.getResourceName()).isEqualTo("wf1.bpmn");

    deployedProcesses = secondDeployment.getValue().getProcessesMetadata();
    assertThat(deployedProcesses).hasSize(1);

    deployedProcess = deployedProcesses.get(0);
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedProcess.getResourceName()).isEqualTo("wf2.bpmn");
  }

  @Test
  public void shouldCreateDeploymentResourceWithCollaboration() {
    // given
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/processes/collaboration.bpmn");
    final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(resourceAsStream);

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource("collaboration.bpmn", modelInstance).deploy();

    // then
    assertThat(deployment.getValue().getProcessesMetadata())
        .extracting(ProcessMetadataValue::getBpmnProcessId)
        .contains("process1", "process2");
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleProcesses() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlResource("process2.bpmn", process2)
            .deploy();

    // then
    assertThat(deployment.getValue().getProcessesMetadata())
        .extracting(ProcessMetadataValue::getBpmnProcessId)
        .contains(processId, processId2);

    assertThat(deployment.getValue().getResources())
        .extracting(DeploymentResource::getResourceName)
        .contains("process.bpmn", "process2.bpmn");

    assertThat(deployment.getValue().getResources())
        .extracting(DeploymentResource::getResource)
        .contains(
            Bpmn.convertToString(process).getBytes(), Bpmn.convertToString(process2).getBytes());
  }

  @Test
  public void shouldWriteProcessRecordsOnDeployment() {
    // given

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlResource("process2.bpmn", process2)
            .deploy()
            .getValue();

    // then
    final var processDefinitionKeyList =
        deployment.getProcessesMetadata().stream()
            .map(ProcessMetadataValue::getProcessDefinitionKey)
            .collect(Collectors.toList());

    final var processRecordKeys =
        RecordingExporter.processRecords()
            .limit(2)
            .map(Record::getKey)
            .collect(Collectors.toList());
    assertThat(processDefinitionKeyList).hasSameElementsAs(processRecordKeys);

    final var firstProcessRecord =
        RecordingExporter.processRecords().withBpmnProcessId(processId).getFirst();
    assertThat(firstProcessRecord).isNotNull();
    assertThat(firstProcessRecord.getValue().getResourceName()).isEqualTo("process.bpmn");
    assertThat(firstProcessRecord.getValue().getVersion()).isEqualTo(1);
    assertThat(firstProcessRecord.getKey())
        .isEqualTo(firstProcessRecord.getValue().getProcessDefinitionKey());

    final var secondProcessRecord =
        RecordingExporter.processRecords().withBpmnProcessId(processId2).getFirst();
    assertThat(secondProcessRecord).isNotNull();
    assertThat(secondProcessRecord.getValue().getResourceName()).isEqualTo("process2.bpmn");
    assertThat(secondProcessRecord.getValue().getVersion()).isEqualTo(1);
    assertThat(secondProcessRecord.getKey())
        .isEqualTo(secondProcessRecord.getValue().getProcessDefinitionKey());
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
  public void shouldIncrementProcessVersions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldIncrementProcessVersions")
            .startEvent()
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource("process1.bpmn", modelInstance).deploy();
    final Record<DeploymentRecordValue> deployment2 =
        ENGINE.deployment().withXmlResource("process2.bpmn", modelInstance).deploy();

    // then
    assertThat(deployment.getValue().getProcessesMetadata().get(0).getVersion()).isEqualTo(1L);
    assertThat(deployment2.getValue().getProcessesMetadata().get(0).getVersion()).isEqualTo(2L);
  }

  @Test
  public void shouldFilterDuplicateProcess() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", process).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", process).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertSameResource(originalProcesses.get(0), repeatedProcesses.get(0));
  }

  @Test
  public void shouldNotFilterWithDifferentResourceName() {
    // given
    final String originalResourceName = "process-1.bpmn";
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource(originalResourceName, process).deploy();

    // when
    final String repeatedResourceName = "process-2.bpmn";
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource(repeatedResourceName, process).deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertDifferentResources(originalProcesses.get(0), repeatedProcesses.get(0));
    assertThat(originalProcesses.get(0).getResourceName()).isEqualTo(originalResourceName);
    assertThat(repeatedProcesses.get(0).getResourceName()).isEqualTo(repeatedResourceName);
  }

  @Test
  public void shouldNotFilterWithDifferentResource() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", process).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", process_V2).deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertDifferentResources(originalProcesses.get(0), repeatedProcesses.get(0));
  }

  @Test
  public void shouldFilterWithTwoEqualResources() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", process)
            .withXmlResource("p2.bpmn", process2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", process)
            .withXmlResource("p2.bpmn", process2)
            .deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isEqualTo(2);

    for (final ProcessMetadataValue process : originalProcesses) {
      assertSameResource(process, findProcess(repeatedProcesses, process.getBpmnProcessId()));
    }
  }

  @Test
  public void shouldFilterWithOneDifferentAndOneEqual() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", process)
            .withXmlResource("p2.bpmn", process2)
            .deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", process)
            .withXmlResource("p2.bpmn", process2_V2)
            .deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isEqualTo(2);

    assertSameResource(
        findProcess(originalProcesses, processId), findProcess(repeatedProcesses, processId));
    assertDifferentResources(
        findProcess(originalProcesses, processId2), findProcess(repeatedProcesses, processId2));
  }

  @Test
  public void shouldNotFilterWithRollbackToPreviousVersion() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("p1.bpmn", process).deploy();
    ENGINE.deployment().withXmlResource("p1.bpmn", process_V2).deploy();

    // when
    final Record<DeploymentRecordValue> rollback =
        ENGINE.deployment().withXmlResource("p1.bpmn", process).deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = rollback.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertDifferentResources(
        findProcess(originalProcesses, processId), findProcess(repeatedProcesses, processId));
  }

  @Test
  public void shouldCreateDeploymentWithMessageStartEventIgnoreExtensionElements() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent("startEvent")
            .messageEventDefinition()
            .message("messageEvent")
            .addExtensionElement(
                ZeebeLoopCharacteristics.class, z -> z.setInputCollection("= inputs"))
            .messageEventDefinitionDone()
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  private ProcessMetadataValue findProcess(
      final List<ProcessMetadataValue> processes, final String processId) {
    return processes.stream()
        .filter(w -> w.getBpmnProcessId().equals(processId))
        .findFirst()
        .orElse(null);
  }

  private void assertSameResource(
      final ProcessMetadataValue original, final ProcessMetadataValue repeated) {
    io.camunda.zeebe.protocol.record.Assertions.assertThat(repeated)
        .hasVersion(original.getVersion())
        .hasProcessDefinitionKey(original.getProcessDefinitionKey())
        .hasResourceName(original.getResourceName())
        .hasBpmnProcessId(original.getBpmnProcessId());
  }

  private void assertDifferentResources(
      final ProcessMetadataValue original, final ProcessMetadataValue repeated) {
    assertThat(original.getProcessDefinitionKey()).isLessThan(repeated.getProcessDefinitionKey());
    assertThat(original.getVersion()).isLessThan(repeated.getVersion());
  }
}
