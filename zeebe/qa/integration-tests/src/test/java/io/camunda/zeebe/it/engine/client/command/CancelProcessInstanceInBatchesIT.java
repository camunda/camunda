/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.client.impl.util.DataSizeUtil.ONE_KB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
final class CancelProcessInstanceInBatchesIT {

  private static final int AMOUNT_OF_ELEMENT_INSTANCES = 100;
  private static final int MAX_MESSAGE_SIZE_KB = 32;

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              c -> {
                c.getCluster()
                    .getNetwork()
                    .setMaxMessageSize(DataSize.ofKilobytes(MAX_MESSAGE_SIZE_KB));
                // Note: this is about the batch for writing to the logstream, not the terminate
                // batch
                c.getProcessing().setMaxCommandsInBatch(1);
                c.getProcessing().setEnableStraightthroughProcessingLoopDetector(false);
              });

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClient() {
    client =
        ZEEBE
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .maxMessageSize(MAX_MESSAGE_SIZE_KB * ONE_KB)
            .build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldCancelInstanceWithMoreChildrenThanTheBatchSizeCanHandle() {
    // given
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess("PROCESS")
            .startEvent()
            .zeebeOutputExpression("0", "count")
            .exclusiveGateway("joining")
            .parallelGateway("split")
            .userTask("userTask")
            .endEvent()
            .moveToLastGateway()
            .exclusiveGateway("forking")
            .sequenceFlowId("sequenceToEnd")
            .conditionExpression("count > " + (AMOUNT_OF_ELEMENT_INSTANCES - 2))
            .endEvent("endEvent")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .scriptTask(
                "increment", task -> task.zeebeExpression("count + 1").zeebeResultVariable("count"))
            .connectTo("joining")
            .done());

    // when
    final ProcessInstanceEvent processInstance = startProcessInstance();
    cancelProcessInstance(processInstance);

    // then
    hasTerminatedProcessInstance(processInstance);
    hasTerminatedAllUserTasks(processInstance);
    hasTerminatedInMultipleBatches(processInstance, processInstance.getProcessInstanceKey());
  }

  @Test
  void shouldCancelSubprocessWithMoreNestedChildrenThanTheBatchSizeCanHandle() {
    // given
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess("PROCESS")
            .startEvent()
            .subProcess(
                "subprocess",
                sp ->
                    sp.embeddedSubProcess()
                        .startEvent()
                        .zeebeOutputExpression("0", "count")
                        .exclusiveGateway("joining")
                        .parallelGateway("split")
                        .userTask("userTask")
                        .endEvent()
                        .moveToLastGateway()
                        .exclusiveGateway("forking")
                        .sequenceFlowId("sequenceToEnd")
                        .conditionExpression("count > " + (AMOUNT_OF_ELEMENT_INSTANCES - 2))
                        .endEvent("endEvent")
                        .moveToLastExclusiveGateway()
                        .defaultFlow()
                        .scriptTask(
                            "increment",
                            task -> task.zeebeExpression("count + 1").zeebeResultVariable("count"))
                        .connectTo("joining"))
            .endEvent()
            .done());

    // when
    final ProcessInstanceEvent processInstance = startProcessInstance();
    final var subProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId("subprocess")
            .getFirst();
    cancelProcessInstance(processInstance);

    // then
    hasTerminatedProcessInstance(processInstance);
    hasTerminatedAllUserTasks(processInstance);
    hasTerminatedInMultipleBatches(processInstance, subProcess.getKey());
  }

  private ProcessInstanceEvent startProcessInstance() {
    final var processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("PROCESS").latestVersion().send().join();
    return processInstance;
  }

  private void cancelProcessInstance(final ProcessInstanceEvent processInstance) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstance.getProcessInstanceKey())
        .withElementId("endEvent")
        .limit(1)
        .await();

    client.newCancelInstanceCommand(processInstance.getProcessInstanceKey()).send();
  }

  private void hasTerminatedProcessInstance(final ProcessInstanceEvent processInstance) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceTerminated()
                .exists())
        .describedAs("Has terminated process instance")
        .isTrue();
  }

  private void hasTerminatedAllUserTasks(final ProcessInstanceEvent processInstance) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .withElementId("userTask")
                .limit(AMOUNT_OF_ELEMENT_INSTANCES))
        .describedAs("Has terminated all " + AMOUNT_OF_ELEMENT_INSTANCES + " user tasks")
        .hasSize(AMOUNT_OF_ELEMENT_INSTANCES);
  }

  private void hasTerminatedInMultipleBatches(
      final ProcessInstanceEvent processInstance, final long batchElementInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceBatchRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .withBatchElementInstanceKey(batchElementInstanceKey)
                .limit(2))
        .describedAs(
            "Has terminated in multiple batches. If this assertion fails please decrease "
                + "the message size, or increase the amount of element instances.")
        .hasSize(2);
  }
}
