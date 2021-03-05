/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

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
