/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Regression test on stable/8.8, exercising the REAL batch-operation move path.
 *
 * <p>This test drives {@link
 * io.camunda.zeebe.engine.processing.batchoperation.handlers.ModifyProcessInstanceBatchOperationExecutor}
 * via {@link AbstractBatchOperationTest#createNewModifyProcessInstanceBatchOperation} — the exact
 * code path used by Operate's process-list "move" (batch) feature. The executor expands the move
 * into a {@code MODIFY} command carrying a {@code batchOperationReference} and the acting user's
 * {@code claims}.
 *
 * <p>Topology: exclusive split -&gt; user task {@code Activity_1sgi1dh} -&gt; exclusive JOIN {@code
 * Gateway_0zrxxs9} -&gt; intermediate signal-throw {@code Event_15l5ajp} -&gt; user task -&gt; end,
 * plus a signal-start receiver process {@code Process_0a5la8z}. Moving the token onto the join
 * gateway auto-proceeds through the gateway and the signal throw, which broadcasts the signal and
 * must start the receiver.
 *
 * <p><b>Before the fix:</b> the signal broadcast emitted by the moved token inherited the MODIFY's
 * {@code batchOperationReference}, so it was classified as a user command and re-authorized with a
 * {@code CREATE_PROCESS_INSTANCE} check against an empty principal (no claims are propagated to the
 * follow-up). The check always failed, raising a {@code PROCESSING_ERROR} that rolled back the
 * whole modification: the token was not moved and no signal was broadcast, yet the batch reported
 * COMPLETED.
 *
 * <p><b>After the fix:</b> a signal broadcast triggered by process execution (a throw event) is
 * marked {@code PRE_AUTHORIZED}, so it is exempt from the user authorization check regardless of
 * the inherited {@code batchOperationReference}. The move now completes, the signal is broadcast,
 * and the receiver process instance is created — matching the single-instance move behaviour.
 */
public final class BatchMoveTokenToGatewaySignalAuthTest extends AbstractBatchOperationTest {

  private static final String SENDER_ID = "Process_nbgivsr";
  private static final String RECEIVER_ID = "Process_0a5la8z";
  private static final String SIGNAL_NAME = "Demo_Signal_Subprozess_StartEvent";
  private static final String SOURCE_ELEMENT = "Activity_1sgi1dh"; // first user task
  private static final String TARGET_GATEWAY = "Gateway_0zrxxs9"; // exclusive join gateway

  private static BpmnModelInstance sender() {
    return Bpmn.createExecutableProcess(SENDER_ID)
        .startEvent()
        .exclusiveGateway("Gateway_131fii5")
        .sequenceFlowId("flow_ja")
        .conditionExpression("=haltpunkt=\"Ja\"")
        .userTask(SOURCE_ELEMENT)
        .zeebeUserTask()
        .exclusiveGateway(TARGET_GATEWAY)
        .intermediateThrowEvent("Event_15l5ajp", t -> t.signal(SIGNAL_NAME))
        .userTask("Activity_0ckjnwl")
        .zeebeUserTask()
        .endEvent()
        .moveToNode("Gateway_131fii5")
        .sequenceFlowId("flow_nein")
        .conditionExpression("=haltpunkt=\"Nein\"")
        .connectTo(TARGET_GATEWAY)
        .done();
  }

  private static BpmnModelInstance receiver() {
    return Bpmn.createExecutableProcess(RECEIVER_ID)
        .startEvent("signalStart")
        .signal(SIGNAL_NAME)
        .endEvent()
        .done();
  }

  private long deployAndAdvanceToSourceUserTask() {
    engine.deployment().withXmlResource(sender()).withXmlResource(receiver()).deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(SENDER_ID)
            .withVariable("haltpunkt", "Ja")
            .create();

    // wait for the source (native) user task to exist
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(SOURCE_ELEMENT)
        .getFirst();
    return processInstanceKey;
  }

  private List<Record<?>> runBatchMoveAndCollectRecords(
      final long processInstanceKey, final Map<String, Object> claims) {
    final long batchOperationKey =
        createNewModifyProcessInstanceBatchOperation(
            Set.of(processInstanceKey), SOURCE_ELEMENT, TARGET_GATEWAY, claims);

    RecordingExporter.batchOperationLifecycleRecords()
        .withBatchOperationKey(batchOperationKey)
        .withIntent(BatchOperationIntent.COMPLETED)
        .getFirst();

    return RecordingExporter.records()
        .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED)
        .collect(Collectors.toList());
  }

  private static boolean modified(final List<Record<?>> records) {
    return records.stream()
        .anyMatch(
            r ->
                r.getValueType() == ValueType.PROCESS_INSTANCE_MODIFICATION
                    && r.getIntent() == ProcessInstanceModificationIntent.MODIFIED);
  }

  private static boolean signalBroadcast(final List<Record<?>> records) {
    return records.stream()
        .anyMatch(
            r -> r.getValueType() == ValueType.SIGNAL && r.getIntent() == SignalIntent.BROADCASTED);
  }

  private static Record<?> modifyRejection(final List<Record<?>> records) {
    return records.stream()
        .filter(
            r ->
                r.getValueType() == ValueType.PROCESS_INSTANCE_MODIFICATION
                    && r.getRecordType() == RecordType.COMMAND_REJECTION
                    && r.getIntent() == ProcessInstanceModificationIntent.MODIFY)
        .findFirst()
        .orElse(null);
  }

  /**
   * Proves the fix: a batch move onto a join gateway whose downstream throw event broadcasts a
   * signal that starts another process is no longer rejected — even though the acting user has no
   * {@code CREATE_PROCESS_INSTANCE} permission on the receiver. The modification is applied, the
   * signal is broadcast and the receiver instance is created.
   */
  @Test
  public void shouldBroadcastSignalAndMoveTokenViaBatchWhenSignalStartIsReached() {
    // given
    final long processInstanceKey = deployAndAdvanceToSourceUserTask();

    // when: the batch MODIFY carries the acting user's claims (mirrors Operate's batch move)
    final Map<String, Object> userClaims =
        Map.of(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final var records = runBatchMoveAndCollectRecords(processInstanceKey, userClaims);

    // then: the modification is NOT rejected (the execution-triggered signal is pre-authorized)
    assertThat(modifyRejection(records))
        .describedAs("the batch MODIFY is no longer rejected by the signal-start auth check")
        .isNull();

    // and: the token is moved and the signal is broadcast
    assertThat(modified(records))
        .describedAs("the modification is applied, so a MODIFIED event is emitted")
        .isTrue();
    assertThat(signalBroadcast(records)).describedAs("the downstream signal is broadcast").isTrue();

    // and: the receiver signal-start process instance is created
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(RECEIVER_ID)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("the signal-start receiver process instance is created")
        .isTrue();
  }
}
