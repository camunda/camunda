/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

/**
 * Flow node types that the duration outlier analysis always skips, because they add no value to it.
 * Do not add types that are worth analyzing, such as service tasks.
 *
 * <p>The values are Camunda 7's {@code org.camunda.bpm.engine.ActivityTypes} names, copied so that
 * Optimize does not need the Camunda 7 engine.
 */
public enum ExcludedFlowNodeType {
  GATEWAY_EXCLUSIVE("exclusiveGateway"),
  GATEWAY_INCLUSIVE("inclusiveGateway"),
  GATEWAY_PARALLEL("parallelGateway"),
  GATEWAY_COMPLEX("complexGateway"),
  GATEWAY_EVENT_BASED("eventBasedGateway"),

  CALL_ACTIVITY("callActivity"),

  BOUNDARY_TIMER("boundaryTimer"),
  BOUNDARY_MESSAGE("boundaryMessage"),
  BOUNDARY_SIGNAL("boundarySignal"),
  BOUNDARY_COMPENSATION("compensationBoundaryCatch"),
  BOUNDARY_ERROR("boundaryError"),
  BOUNDARY_ESCALATION("boundaryEscalation"),
  BOUNDARY_CANCEL("cancelBoundaryCatch"),
  BOUNDARY_CONDITIONAL("boundaryConditional"),

  START_EVENT("startEvent"),
  START_EVENT_TIMER("startTimerEvent"),
  START_EVENT_MESSAGE("messageStartEvent"),
  START_EVENT_SIGNAL("signalStartEvent"),
  START_EVENT_ESCALATION("escalationStartEvent"),
  START_EVENT_COMPENSATION("compensationStartEvent"),
  START_EVENT_ERROR("errorStartEvent"),
  START_EVENT_CONDITIONAL("conditionalStartEvent"),

  INTERMEDIATE_EVENT_CATCH("intermediateCatchEvent"),
  INTERMEDIATE_EVENT_MESSAGE("intermediateMessageCatch"),
  INTERMEDIATE_EVENT_TIMER("intermediateTimer"),
  INTERMEDIATE_EVENT_LINK("intermediateLinkCatch"),
  INTERMEDIATE_EVENT_SIGNAL("intermediateSignalCatch"),
  INTERMEDIATE_EVENT_CONDITIONAL("intermediateConditional"),
  INTERMEDIATE_EVENT_THROW("intermediateThrowEvent"),
  INTERMEDIATE_EVENT_SIGNAL_THROW("intermediateSignalThrow"),
  INTERMEDIATE_EVENT_COMPENSATION_THROW("intermediateCompensationThrowEvent"),
  INTERMEDIATE_EVENT_MESSAGE_THROW("intermediateMessageThrowEvent"),
  INTERMEDIATE_EVENT_NONE_THROW("intermediateNoneThrowEvent"),
  INTERMEDIATE_EVENT_ESCALATION_THROW("intermediateEscalationThrowEvent"),

  END_EVENT_ERROR("errorEndEvent"),
  END_EVENT_CANCEL("cancelEndEvent"),
  END_EVENT_TERMINATE("terminateEndEvent"),
  END_EVENT_MESSAGE("messageEndEvent"),
  END_EVENT_SIGNAL("signalEndEvent"),
  END_EVENT_COMPENSATION("compensationEndEvent"),
  END_EVENT_ESCALATION("escalationEndEvent"),
  END_EVENT_NONE("noneEndEvent");

  private final String value;

  ExcludedFlowNodeType(final String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
