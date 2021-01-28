/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random.steps;

import io.zeebe.engine.util.random.ExecutionPathSegment;

public final class StartProcess extends AbstractExecutionStep {

  private final String processId;

  public StartProcess(String processId, ExecutionPathSegment pathSegment) {
    this.processId = processId;
    variables.putAll(pathSegment.collectVariables());
  }

  public String getProcessId() {
    return processId;
  }
}
