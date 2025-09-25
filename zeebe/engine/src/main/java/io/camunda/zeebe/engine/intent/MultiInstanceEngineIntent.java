/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum MultiInstanceEngineIntent implements ProcessInstanceRelatedEngineIntent {
  INPUT_COLLECTION_EVALUATED(0);

  final short value;

  MultiInstanceEngineIntent(final int value) {
    this.value = (short) value;
  }

  public static EngineIntent from(final short value) {
    if (value == 0) {
      return INPUT_COLLECTION_EVALUATED;
    }
    return EngineIntent.UNKNOWN;
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return true;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return true;
  }
}
