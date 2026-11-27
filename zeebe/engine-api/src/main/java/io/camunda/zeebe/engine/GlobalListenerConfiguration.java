/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record GlobalListenerConfiguration(
    String id,
    List<String> eventTypes,
    String type,
    String retries,
    boolean afterNonGlobal,
    int priority,
    GlobalListenerType listenerType) {

  /** The event types a global listener may subscribe to: the task listener events and "all". */
  public static final Set<String> VALID_EVENT_TYPES =
      Stream.concat(
              Stream.of(GlobalListenerRecord.ALL_EVENT_TYPES),
              Stream.of(ZeebeTaskListenerEventType.values()).map(Enum::name))
          .collect(Collectors.toUnmodifiableSet());

  public GlobalListenerRecord toRecord() {
    return new GlobalListenerRecord()
        .setId(this.id())
        .setType(this.type())
        .setEventTypes(this.eventTypes())
        .setRetries(Integer.parseInt(this.retries()))
        .setAfterNonGlobal(this.afterNonGlobal())
        .setPriority(this.priority())
        .setSource(GlobalListenerSource.CONFIGURATION)
        .setListenerType(this.listenerType());
  }
}
