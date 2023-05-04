/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class CancelProcessInstanceInBatchesTest {

  private static final int AMOUNT_OF_ELEMENT_INSTANCES = 100;
  private static final int MAX_MESSAGE_SIZE_KB = 32;
  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(MAX_MESSAGE_SIZE_KB));
            // Note: this is a bout the batch for writing to the logstream, not the terminate batch
            cfg.getProcessing().setMaxCommandsInBatch(1);
          });
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCancelInstanceWithMoreChildrenThanTheBatchSizeCanHandle() {
    // given
    CLIENT_RULE.deployProcess(
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
  public void shouldCancelSubprocessWithMoreNestedChildrenThanTheBatchSizeCanHandle() {
    // given
    CLIENT_RULE.deployProcess(
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
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("PROCESS")
            .latestVersion()
            .send()
            .join();
    return processInstance;
  }

  private void cancelProcessInstance(final ProcessInstanceEvent processInstance) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstance.getProcessInstanceKey())
        .withElementId("endEvent")
        .limit(1)
        .await();

    CLIENT_RULE
        .getClient()
        .newCancelInstanceCommand(processInstance.getProcessInstanceKey())
        .send();
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
