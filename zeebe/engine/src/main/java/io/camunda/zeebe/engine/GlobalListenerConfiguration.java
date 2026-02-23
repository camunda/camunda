/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;

public record GlobalListenerConfiguration(
    String id,
    List<String> eventTypes,
    String type,
    String retries,
    boolean afterNonGlobal,
    int priority,
    GlobalListenerType listenerType) {

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
