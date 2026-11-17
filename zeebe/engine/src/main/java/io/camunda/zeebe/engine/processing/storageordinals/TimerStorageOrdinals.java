/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;

/** Resolves the storage ordinal to set on timer records rebuilt from timer state. */
public final class TimerStorageOrdinals {

  private TimerStorageOrdinals() {}

  /**
   * Start event timers belong to the process definition, not to an instance, so they are never
   * ordinal-controlled. Their persisted value is not trusted: rows written before the ordinal
   * existed hold the default ordinal value (i.e. `0`).
   */
  public static int of(final TimerInstance timer) {
    return timer.getElementInstanceKey() == TimerInstance.NO_ELEMENT_INSTANCE
        ? StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED
        : timer.getStorageOrdinal();
  }
}
