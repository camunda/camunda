/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import java.util.stream.Stream;

public record GlobalListenerConfiguration(
    String id,
    List<String> eventTypes,
    String type,
    String retries,
    boolean afterNonGlobal,
    int priority,
    GlobalListenerType listenerType) {

  public static final String ALL_EVENT_TYPES = "all";

  // List of all possible task listener event types as strings, to be used while validating
  // the configuration (ZeebeTaskListenerEventType is not accessible from the broker module)
  public static final List<String> TASK_LISTENER_EVENT_TYPES =
      Stream.of(ZeebeTaskListenerEventType.values()).map(Enum::name).toList();
}
