/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import java.time.Duration;
import java.util.function.Consumer;

public final class ZeebeBpmnModels {

  public static final String ACTIVATE_ELEMENTS = "activateElements";
  public static final String ADHOC_SUB_PROCESS = "adhocSubProcess";
  public static final String START_EVENT = "startEvent";
  public static final String SERVICE_TASK = "service_task";
  public static final String SEND_TASK = "send_task";
  public static final String USER_TASK = "user_task";
  public static final String END_EVENT = "end";
  public static final String END_EVENT_2 = "end2";
  public static final String TERMINATE_END_EVENT = "terminate-end";
  public static final String CATCH_EVENT = "catchEvent";
  public static final String CONVERGING_GATEWAY = "converging_gateway";
  public static final String DIVERGING_GATEWAY = "diverging_gateway";
  public static final String SIGNAL_START_EVENT = "signalStartEvent";
  public static final String SIGNAL_START_INT_SUB_PROCESS =
      "signalStartInterruptingSubProcessEvent";
  public static final String SIGNAL_START_NON_INT_SUB_PROCESS =
      "signalStartNonInterruptingSubProcessEvent";
  public static final String SIGNAL_GATEWAY_CATCH =
      "signalIntermediateCatchEventAttachedToEventBasedGateway";
  public static final String SIGNAL_THROW = "signalIntermediateThrowEvent";
  public static final String SIGNAL_CATCH = "signalIntermediateCatchEvent";
  public static final String SIGNAL_INTERRUPTING_BOUNDARY =
      "signalIntermediateBoundaryInterruptingEvent";
  public static final String SIGNAL_NON_INTERRUPTING_BOUNDARY =
      "signalIntermediateBoundaryNonInterruptingEvent";
  public static final String SIGNAL_PROCESS_WAIT_FOR_FIRST_SIGNAL_TASK =
      "signalProcessServiceTask1";
  public static final String SIGNAL_PROCESS_WAIT_FOR_SECOND_SIGNAL_TASK =
      "signalProcessServiceTask2";
  public static final String SIGNAL_PROCESS_WAIT_FOR_THIRD_SIGNAL_GATEWAY = "eventBasedGateway";
  public static final String SIGNAL_PROCESS_END = "signalEndEvent";
  public static final String SIGNAL_PROCESS_FIRST_SIGNAL = "nonInterruptingBoundarySignal";
  public static final String SIGNAL_PROCESS_SECOND_SIGNAL = "interruptingBoundarySignal";
  public static final String SIGNAL_PROCESS_THIRD_SIGNAL = "eventBasedGatewaySignal";
  public static final String SERVICE_TASK_WITH_COMPENSATION_EVENT = "compensationEvent";
  public static final String TASK = "task";
  public static final String COMPENSATION_EVENT_TASK = "compensationEventTask";
  public static final String CONDITIONAL_START_EVENT = "conditionalStartEvent";
  public static final String CONDITIONAL_INTERMEDIATE_CATCH = "conditionalIntermediateCatchEvent";
  public static final String CONDITIONAL_NON_INTERRUPTING_BOUNDARY =
      "conditionalBoundaryNonInterrupting";
  public static final String CONDITIONAL_INTERRUPTING_BOUNDARY = "conditionalBoundaryInterrupting";
  public static final String CONDITIONAL_NON_INT_SUB_PROCESS =
      "conditionalStartNonInterruptingSubProcess";
  public static final String CONDITIONAL_INT_SUB_PROCESS = "conditionalStartInterruptingSubProcess";
  public static final String CONDITIONAL_PROCESS_SERVICE_TASK_1 = "conditionalProcessServiceTask1";
  public static final String CONDITIONAL_PROCESS_SERVICE_TASK_2 = "conditionalProcessServiceTask2";
  public static final String CONDITIONAL_PROCESS_END = "conditionalEndEvent";
  public static final String VERSION_TAG = "v1";

  private ZeebeBpmnModels() {}

  public static BpmnModelInstance createStartEndProcess(final String processName) {
    return createStartEndProcess(processName, null);
  }

  public static BpmnModelInstance createStartEndProcess(
      final String processName, final String processId) {
    ProcessBuilder executableProcess = Bpmn.createExecutableProcess();
    if (processId != null) {
      executableProcess = executableProcess.id(processId);
    }
    return executableProcess
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createSimpleServiceTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .serviceTask(SERVICE_TASK)
        .zeebeJobType(SERVICE_TASK)
        .name(SERVICE_TASK)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createAdHocSubProcess(
      final String processName, final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(processName)
        .name(processName)
        .startEvent(START_EVENT)
        .adHocSubProcess(ADHOC_SUB_PROCESS, modifier)
        .endEvent(END_EVENT)
        .done();
  }

  public static BpmnModelInstance createSingleStartDoubleEndEventProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
        .name(processName)
        .startEvent(START_EVENT)
        .parallelGateway()
        .endEvent(END_EVENT)
        .moveToLastGateway()
        .endEvent(END_EVENT_2)
        .done();
  }

  public static BpmnModelInstance createTerminateEndEventProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
        .name(processName)
        .startEvent()
        .endEvent(TERMINATE_END_EVENT, EndEventBuilder::terminate)
        .done();
  }

  public static BpmnModelInstance createIncidentProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .intermediateCatchEvent(
            CATCH_EVENT,
            e -> e.message(m -> m.name("catch").zeebeCorrelationKeyExpression("orderId")))
        .endEvent(END_EVENT_2)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createSimpleUserTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .versionTag(VERSION_TAG)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .userTask(USER_TASK)
        .id(USER_TASK)
        .name(USER_TASK)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createSimpleNativeUserTaskProcessWithAssignee(
      final String processName, final String dueDate, final String assignee) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .userTask(USER_TASK)
        .zeebeUserTask()
        .id(USER_TASK)
        .name(USER_TASK)
        .zeebeDueDate(dueDate)
        .zeebeAssignee(assignee)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createSimpleNativeUserTaskProcess(
      final String processName, final String dueDate) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .userTask(USER_TASK)
        .zeebeUserTask()
        .id(USER_TASK)
        .name(USER_TASK)
        .zeebeDueDate(dueDate)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createSimpleNativeUserTaskProcessWithCandidateGroup(
      final String processName, final String dueDate, final String candidateGroup) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .userTask(USER_TASK)
        .zeebeUserTask()
        .id(USER_TASK)
        .name(USER_TASK)
        .zeebeDueDate(dueDate)
        .zeebeCandidateGroups(candidateGroup)
        .endEvent(END_EVENT)
        .name(null)
        .done();
  }

  public static BpmnModelInstance createLoopingProcess(final String processName) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .exclusiveGateway(CONVERGING_GATEWAY)
        .serviceTask(SERVICE_TASK)
        .zeebeJobType(SERVICE_TASK)
        .exclusiveGateway(DIVERGING_GATEWAY)
        .condition("End process", "=loop=false")
        .endEvent(END_EVENT)
        .moveToNode(DIVERGING_GATEWAY)
        .condition("Do Loop", "=loop=true")
        .connectTo(CONVERGING_GATEWAY)
        .done();
  }

  public static BpmnModelInstance createInclusiveGatewayProcess(final String processName) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .inclusiveGateway(DIVERGING_GATEWAY)
        .sequenceFlowId("s1")
        .conditionExpression("= contains(varName,\"a\")")
        .endEvent(END_EVENT)
        .moveToLastGateway()
        .sequenceFlowId("s2")
        .conditionExpression("= contains(varName,\"b\")")
        .endEvent(END_EVENT_2)
        .done();
  }

  public static BpmnModelInstance createCompensationEventProcess() {
    return Bpmn.createExecutableProcess()
        .startEvent()
        .serviceTask(
            SERVICE_TASK_WITH_COMPENSATION_EVENT,
            task ->
                task.zeebeJobType(SERVICE_TASK_WITH_COMPENSATION_EVENT)
                    .boundaryEvent()
                    .compensation(
                        compensation ->
                            compensation
                                .serviceTask(COMPENSATION_EVENT_TASK)
                                .zeebeJobType(COMPENSATION_EVENT_TASK)))
        .endEvent()
        .compensateEventDefinition()
        .done();
  }

  public static BpmnModelInstance createInclusiveGatewayProcessWithConverging(
      final String processName) {
    return Bpmn.createExecutableProcess(processName)
        .startEvent(START_EVENT)
        .inclusiveGateway(DIVERGING_GATEWAY)
        .sequenceFlowId("s1")
        .conditionExpression("= contains(varName,\"a\")")
        .inclusiveGateway(CONVERGING_GATEWAY)
        .endEvent(END_EVENT)
        .done();
  }

  public static BpmnModelInstance createSendTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess()
        .name(processName)
        .startEvent(START_EVENT)
        .sendTask(SEND_TASK)
        .zeebeJobType(SEND_TASK)
        .done();
  }

  public static BpmnModelInstance createProcessWith83SignalEvents(final String startSignalName) {
    // @formatter:off
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess("signalProcess");

    processBuilder
        .eventSubProcess("interruptingSubProcess")
        .startEvent(
            SIGNAL_START_INT_SUB_PROCESS,
            s -> s.signal("signalToStartInterruptingSubProcess").interrupting(true))
        .endEvent("interruptingSubProcessEnd");

    processBuilder
        .eventSubProcess("nonInterruptingSubProcess")
        .startEvent(
            SIGNAL_START_NON_INT_SUB_PROCESS,
            s -> s.signal("signalToStartNonInterruptingSubProcess").interrupting(false))
        .endEvent(
            "nonInterruptingSubProcessEnd",
            s -> s.signal("signalToContinueProcessAfterNonInterruptingSubProcess"));

    return processBuilder
        .startEvent(SIGNAL_START_EVENT)
        .signal(startSignalName)
        .intermediateThrowEvent(
            SIGNAL_THROW, t -> t.signal("signalToStartNonInterruptingSubProcess"))
        .intermediateCatchEvent(
            SIGNAL_CATCH, c -> c.signal("signalToContinueProcessAfterNonInterruptingSubProcess"))
        .serviceTask(SIGNAL_PROCESS_WAIT_FOR_FIRST_SIGNAL_TASK)
        .zeebeJobType(SERVICE_TASK)
        .boundaryEvent(
            SIGNAL_NON_INTERRUPTING_BOUNDARY,
            b ->
                b.signal(
                    SIGNAL_PROCESS_FIRST_SIGNAL) // this signal needs thrown in test to continue
            )
        .cancelActivity(false) // make boundary event non interrupting
        .serviceTask(SIGNAL_PROCESS_WAIT_FOR_SECOND_SIGNAL_TASK)
        .zeebeJobType(SERVICE_TASK)
        .boundaryEvent(
            SIGNAL_INTERRUPTING_BOUNDARY,
            b ->
                b.signal(
                    SIGNAL_PROCESS_SECOND_SIGNAL) // this signal needs thrown in test to continue
            )
        .cancelActivity(true) // make boundary event interrupting
        .eventBasedGateway(SIGNAL_PROCESS_WAIT_FOR_THIRD_SIGNAL_GATEWAY)
        .intermediateCatchEvent(
            "timerEvent",
            t ->
                t.timerWithDuration(Duration.ofMinutes(1)) // timer required for event based gateway
            )
        .moveToLastGateway()
        .intermediateCatchEvent(
            SIGNAL_GATEWAY_CATCH,
            c ->
                c.signal(
                    SIGNAL_PROCESS_THIRD_SIGNAL) // this signal needs thrown in test to continue
            )
        .endEvent(SIGNAL_PROCESS_END, e -> e.signal("signalToStartInterruptingSubProcess"))
        .done();
    // @formatter:on
  }

  public static BpmnModelInstance createProcessWithConditionalEvents() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess("conditionalProcess");

    processBuilder
        .eventSubProcess("interruptingSubProcess")
        .startEvent(CONDITIONAL_INT_SUB_PROCESS)
        .condition(c -> c.condition("= triggerSubProcessInt = true"))
        .interrupting(true)
        .endEvent("interruptingSubProcessEnd");

    processBuilder
        .eventSubProcess("nonInterruptingSubProcess")
        .startEvent(CONDITIONAL_NON_INT_SUB_PROCESS)
        .condition(c -> c.condition("= triggerSubProcessNonInt = true"))
        .interrupting(false)
        .endEvent("nonInterruptingSubProcessEnd");

    return processBuilder
        .startEvent(CONDITIONAL_START_EVENT)
        .condition(c -> c.condition("= triggerStart = true"))
        .serviceTask(CONDITIONAL_PROCESS_SERVICE_TASK_1)
        .zeebeJobType(SERVICE_TASK)
        .boundaryEvent(CONDITIONAL_NON_INTERRUPTING_BOUNDARY)
        .condition(c -> c.condition("= triggerBoundaryNonInt = true"))
        .cancelActivity(false)
        .endEvent("boundaryNonIntEnd")
        .moveToActivity(CONDITIONAL_PROCESS_SERVICE_TASK_1)
        .intermediateCatchEvent(CONDITIONAL_INTERMEDIATE_CATCH)
        .condition(c -> c.condition("= triggerIntermediate = true"))
        .serviceTask(CONDITIONAL_PROCESS_SERVICE_TASK_2)
        .zeebeJobType(SERVICE_TASK)
        .boundaryEvent(CONDITIONAL_INTERRUPTING_BOUNDARY)
        .condition(c -> c.condition("= triggerBoundaryInt = true"))
        .cancelActivity(true)
        .serviceTask("serviceTaskAfterBoundary")
        .zeebeJobType("waitTask")
        .endEvent("boundaryIntEnd")
        .moveToActivity(CONDITIONAL_PROCESS_SERVICE_TASK_2)
        .endEvent(CONDITIONAL_PROCESS_END)
        .done();
  }
}
