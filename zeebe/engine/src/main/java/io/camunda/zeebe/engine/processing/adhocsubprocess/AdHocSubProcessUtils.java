/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Collectors;

public class AdHocSubProcessUtils {

  private static final String AHSP_ELEMENT_ACTIVATION_FAILED_NOT_FOUND =
      "Failed to activate ad-hoc elements. No BPMN elements found with ids: %s.";

  public static Either<Failure, List<String>> validateActiveElementAreInProcess(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final List<String> activateElementsCollection) {

    final List<String> elementsNotFound =
        activateElementsCollection.stream()
            .filter(elementId -> !adHocSubProcess.getAdHocActivitiesById().containsKey(elementId))
            .toList();

    if (elementsNotFound.isEmpty()) {
      return Either.right(activateElementsCollection);

    } else {
      final String elementIds =
          elementsNotFound.stream().map("'%s'"::formatted).collect(Collectors.joining(", "));
      return Either.left(
          new Failure(
              AHSP_ELEMENT_ACTIVATION_FAILED_NOT_FOUND.formatted(elementIds),
              ErrorType.EXTRACT_VALUE_ERROR));
    }
  }
}
