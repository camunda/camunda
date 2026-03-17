/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class BpmnLinkedResourceBehavior {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ResourceState resourceState;

  public BpmnLinkedResourceBehavior(
      final KeyGenerator keyGenerator, final Writers writers, final ResourceState resourceState) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    this.resourceState = resourceState;
  }
}
