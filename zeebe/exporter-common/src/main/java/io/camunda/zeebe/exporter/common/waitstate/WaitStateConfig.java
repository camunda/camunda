/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateElementType;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import java.util.Map;
import java.util.Optional;

/**
 * Maps {@link WaitStateElementType} values to their default {@link WaitStateType}.
 *
 * <p>Only unambiguous mappings are registered here. Event-driven elements such as catch events,
 * boundary events, and event-based gateways carry an event definition that determines the wait
 * state type at runtime (timer vs. message vs. signal), so their resolution must happen in the
 * concrete transformer rather than via this map.
 */
public final class WaitStateConfig {

  private static final Map<WaitStateElementType, WaitStateType> ELEMENT_TYPE_TO_WAIT_STATE_TYPE =
      Map.ofEntries(
          Map.entry(WaitStateElementType.SERVICE_TASK, WaitStateType.JOB),
          Map.entry(WaitStateElementType.SEND_TASK, WaitStateType.JOB),
          Map.entry(WaitStateElementType.BUSINESS_RULE_TASK, WaitStateType.JOB),
          Map.entry(WaitStateElementType.SCRIPT_TASK, WaitStateType.JOB),
          Map.entry(WaitStateElementType.USER_TASK, WaitStateType.USER_TASK),
          Map.entry(WaitStateElementType.RECEIVE_TASK, WaitStateType.MESSAGE),
          Map.entry(WaitStateElementType.CALL_ACTIVITY, WaitStateType.CALL_ACTIVITY));

  private WaitStateConfig() {}

  public static Optional<WaitStateType> getWaitStateType(final WaitStateElementType elementType) {
    return Optional.ofNullable(ELEMENT_TYPE_TO_WAIT_STATE_TYPE.get(elementType));
  }
}
