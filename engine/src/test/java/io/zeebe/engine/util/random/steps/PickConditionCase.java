/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random.steps;

// this class could also be called "Set variable when starting the process so that the engine will
// select a certain condition"
public final class PickConditionCase extends AbstractExecutionStep {

  private final String forkingGatewwayId;
  private final String edgeId;

  public PickConditionCase(String forkingGatewwayId, String edgeId) {
    this.forkingGatewwayId = forkingGatewwayId;
    this.edgeId = edgeId;
    variables.put(edgeId, true);
  }
}
