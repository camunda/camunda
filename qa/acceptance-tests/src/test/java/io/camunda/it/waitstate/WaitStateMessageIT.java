/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

import static io.camunda.it.util.TestHelper.cancelInstance;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.MessageWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the element instance wait-state search command for message-based wait states: Receive
 * Tasks and Intermediate Message Catch Events. Covers add (CREATED), removal on correlation
 * (CORRELATED), removal on cancellation (DELETED), and element-id update on migration (MIGRATED).
 */
@MultiDbTest
public class WaitStateMessageIT {

  private static final String RECEIVE_TASK_PROCESS_ID = "waitStateReceiveTaskProcess";
  private static final String CATCH_EVENT_PROCESS_ID = "waitStateCatchEventProcess";
  private static final String RECEIVE_TASK = "receive-task";
  private static final String CATCH_EVENT = "catch-event";
  private static final String ORDER_MESSAGE = "order-received";
  private static final String PAYMENT_MESSAGE = "payment-processed";

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance receiveTaskProcess =
        Bpmn.createExecutableProcess(RECEIVE_TASK_PROCESS_ID)
            .startEvent()
            .receiveTask(RECEIVE_TASK)
            .message(m -> m.name(ORDER_MESSAGE).zeebeCorrelationKeyExpression("orderId"))
            .endEvent()
            .done();

    final BpmnModelInstance catchEventProcess =
        Bpmn.createExecutableProcess(CATCH_EVENT_PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(CATCH_EVENT)
            .message(m -> m.name(PAYMENT_MESSAGE).zeebeCorrelationKeyExpression("paymentId"))
            .endEvent()
            .done();

    deployResource(camundaClient, receiveTaskProcess, "waitStateReceiveTaskProcess.bpmn");
    deployResource(camundaClient, catchEventProcess, "waitStateCatchEventProcess.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 2);

    startProcessInstance(camundaClient, RECEIVE_TASK_PROCESS_ID, Map.of("orderId", "order-abc"));
    startProcessInstance(camundaClient, CATCH_EVENT_PROCESS_ID, Map.of("paymentId", "payment-xyz"));
    waitForProcessInstancesToStart(camundaClient, 2);

    waitForWaitStates(2);
  }

  @Test
  void shouldReturnWaitStateForReceiveTask() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementId(RECEIVE_TASK))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var item = result.items().getFirst();
    assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(item.getElementId()).isEqualTo(RECEIVE_TASK);
    assertThat(item.getElementType()).isEqualTo(WaitStateElementType.RECEIVE_TASK);
    assertThat(item.getDetails()).isInstanceOf(MessageWaitStateDetails.class);
    final var details = (MessageWaitStateDetails) item.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(details.getMessageName()).isEqualTo(ORDER_MESSAGE);
    assertThat(details.getCorrelationKey()).isEqualTo("order-abc");
  }

  @Test
  void shouldReturnWaitStateForIntermediateCatchEvent() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementId(CATCH_EVENT))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var item = result.items().getFirst();
    assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(item.getElementId()).isEqualTo(CATCH_EVENT);
    assertThat(item.getElementType()).isEqualTo(WaitStateElementType.INTERMEDIATE_CATCH_EVENT);
    assertThat(item.getDetails()).isInstanceOf(MessageWaitStateDetails.class);
    final var details = (MessageWaitStateDetails) item.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(details.getMessageName()).isEqualTo(PAYMENT_MESSAGE);
    assertThat(details.getCorrelationKey()).isEqualTo("payment-xyz");
  }

  @Test
  void shouldFilterByMessageWaitStateType() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.MESSAGE))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(result.items())
        .allSatisfy(item -> assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE));
  }

  @Test
  void shouldRemoveWaitStateOnMessageCorrelated() {
    // given — isolated instance so correlation does not affect shared @BeforeAll state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateMessageCorrelationProcess")
            .startEvent()
            .receiveTask("corr-receive-task")
            .message(m -> m.name("corr-msg").zeebeCorrelationKeyExpression("corrKey"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateMessageCorrelationProcess.bpmn");

    final long pik =
        startProcessInstance(
                camundaClient, "waitStateMessageCorrelationProcess", Map.of("corrKey", "k-corr"))
            .getProcessInstanceKey();

    Awaitility.await("wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when — correlate the message, which ends the wait
    camundaClient
        .newCorrelateMessageCommand()
        .messageName("corr-msg")
        .correlationKey("k-corr")
        .send()
        .join();

    // then — wait state is removed
    Awaitility.await("wait state should be removed after message correlation")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldRemoveWaitStateWhenProcessInstanceIsCanceled() {
    // given — isolated instance
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateMessageCancellationProcess")
            .startEvent()
            .receiveTask("cancel-receive-task")
            .message(m -> m.name("cancel-msg").zeebeCorrelationKeyExpression("cancelKey"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateMessageCancellationProcess.bpmn");

    final var instance =
        startProcessInstance(
            camundaClient, "waitStateMessageCancellationProcess", Map.of("cancelKey", "k-cancel"));
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when — cancel the process instance, which deletes the subscription
    cancelInstance(camundaClient, instance);

    // then — wait state is removed
    Awaitility.await("wait state should be removed after process cancellation")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldUpdateWaitStateElementIdWhenProcessInstanceIsMigrated() {
    // given — V1 has a receive task "source-receive"; V2 remaps it to "target-receive"
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess("waitStateMsgMigrationProcessV1")
            .startEvent()
            .receiveTask("source-receive")
            .message(m -> m.name("migration-msg").zeebeCorrelationKeyExpression("migKey"))
            .endEvent()
            .done();
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess("waitStateMsgMigrationProcessV2")
            .startEvent()
            .receiveTask("target-receive")
            .message(m -> m.name("migration-msg").zeebeCorrelationKeyExpression("migKey"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, v1, "waitStateMsgMigrationProcessV1.bpmn");
    final long v2Key =
        deployProcessAndWaitForIt(camundaClient, v2, "waitStateMsgMigrationProcessV2.bpmn")
            .getProcessDefinitionKey();

    final long pik =
        startProcessInstance(
                camundaClient, "waitStateMsgMigrationProcessV1", Map.of("migKey", "mig-xyz"))
            .getProcessInstanceKey();

    Awaitility.await("wait state with source-receive should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .allSatisfy(
                        item -> assertThat(item.getElementId()).isEqualTo("source-receive")));

    // when — migrate, remapping the receive task to the target element id
    camundaClient
        .newMigrateProcessInstanceCommand(pik)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(v2Key)
                .addMappingInstruction("source-receive", "target-receive")
                .build())
        .send()
        .join();

    // then — wait state reflects the new element id
    Awaitility.await("wait state should be updated with target-receive element id")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .allSatisfy(
                        item -> assertThat(item.getElementId()).isEqualTo("target-receive")));

    // cleanup
    camundaClient.newCancelInstanceCommand(pik).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
    Awaitility.await("wait state should be removed after cleanup")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  private static void waitForWaitStates(final int expectedCount) {
    Awaitility.await("should export %d MESSAGE wait states".formatted(expectedCount))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<ElementInstanceWaitStateResult> items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.waitStateType(WaitStateType.MESSAGE))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(expectedCount);
            });
  }
}
