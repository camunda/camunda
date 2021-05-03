/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;

/** Applies the state changes for a specific event. */
public interface EventApplier {

  /**
   * Apply the state changes of the given event.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param recordValue the value of the event
   */
  void applyState(long key, Intent intent, RecordValue recordValue);
}
