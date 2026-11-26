/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * The case definition of a process marked with {@code zeebe:case}, read at deploy time.
 *
 * @param titleVariable the name of the variable that holds the case title
 * @param stages the top-level stages of the process in document order
 */
@NullMarked
public record ExecutableCase(DirectBuffer titleVariable, List<ExecutableStage> stages) {

  /**
   * A top-level stage of a case.
   *
   * @param elementId the id of the sub-process element that models the stage
   * @param name the name of the stage, empty if it has none
   */
  public record ExecutableStage(String elementId, String name) {}
}
