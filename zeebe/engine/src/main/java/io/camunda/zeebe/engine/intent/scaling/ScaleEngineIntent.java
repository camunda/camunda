/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent.scaling;

import io.camunda.zeebe.engine.intent.EngineIntent;

public enum ScaleEngineIntent implements EngineIntent {
  SCALE_UP((short) 1, false),
  SCALING_UP((short) 2, true),
  SCALED_UP((short) 3, true),
  STATUS((short) 4, false),
  STATUS_RESPONSE((short) 5, true),
  MARK_PARTITION_BOOTSTRAPPED((short) 6, false),
  PARTITION_BOOTSTRAPPED((short) 7, true);

  // A static field is needed as values() would allocate at every call
  private static final ScaleEngineIntent[] INTENTS = values();
  private final short value;
  private final boolean isEvent;

  ScaleEngineIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }

  public static EngineIntent from(final short intent) {
    try {
      return INTENTS[intent - 1];
    } catch (final ArrayIndexOutOfBoundsException e) {
      return UNKNOWN;
    }
  }
}
