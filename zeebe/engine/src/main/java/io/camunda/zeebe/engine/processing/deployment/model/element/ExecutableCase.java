/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.List;
import java.util.Optional;

/**
 * The case definition of a process marked with {@code zeebe:case}: the stage graph computed at
 * deploy time from the BPMN model.
 *
 * @param titleVariable the name of the variable holding the case title, or null if none
 */
public record ExecutableCase(
    String titleVariable, List<String> startElementIds, List<ExecutableStage> stages) {

  public Optional<ExecutableStage> getStage(final String elementId) {
    return stages.stream().filter(stage -> stage.elementId().equals(elementId)).findFirst();
  }

  public record ExecutableStage(
      String elementId,
      String name,
      boolean alternative,
      boolean adHoc,
      List<String> nextElementIds) {}
}
