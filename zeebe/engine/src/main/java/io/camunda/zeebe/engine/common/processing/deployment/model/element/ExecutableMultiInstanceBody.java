/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

public class ExecutableMultiInstanceBody extends ExecutableActivity {

  private final ExecutableLoopCharacteristics loopCharacteristics;
  private final ExecutableActivity innerActivity;

  public ExecutableMultiInstanceBody(
      final String id,
      final ExecutableLoopCharacteristics loopCharacteristics,
      final ExecutableActivity innerActivity) {
    super(id);
    this.loopCharacteristics = loopCharacteristics;
    this.innerActivity = innerActivity;
  }

  public ExecutableLoopCharacteristics getLoopCharacteristics() {
    return loopCharacteristics;
  }

  public ExecutableActivity getInnerActivity() {
    return innerActivity;
  }
}
