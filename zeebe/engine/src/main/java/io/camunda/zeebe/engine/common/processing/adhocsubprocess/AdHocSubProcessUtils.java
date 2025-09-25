/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.adhocsubprocess;

import io.camunda.zeebe.engine.common.processing.Rejection;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class AdHocSubProcessUtils {

  private static final String ERROR_MSG_ELEMENTS_NOT_FOUND =
      "Expected to activate activities for ad-hoc sub-process with key '%s', but the given elements %s do not exist.";

  private static final String
      ERROR_MSG_AHSP_CANNOT_ACTIVATE_ELEMENTS_WITH_COMPLETE_CONDITION_FULFILLED =
          "No elements can be activated for ad-hoc sub-process with key '%s' because the completion condition is fulfilled.";

  public static Either<Rejection, List<String>> validateActivateElementsExistInAdHocSubProcess(
      final long adHocSubProcessKey,
      final ExecutableAdHocSubProcess adHocSubProcess,
      final List<String> activateElementsCollection) {

    final List<String> elementsNotFound =
        activateElementsCollection.stream()
            .filter(elementId -> !adHocSubProcess.getAdHocActivitiesById().containsKey(elementId))
            .toList();

    if (elementsNotFound.isEmpty()) {
      return Either.right(activateElementsCollection);

    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              ERROR_MSG_ELEMENTS_NOT_FOUND.formatted(adHocSubProcessKey, elementsNotFound)));
    }
  }

  public static Either<Rejection, List<String>>
      validateThatCompletionConditionIsNotFulfilledWhenActivatingElements(
          final long adHocSubProcessKey,
          final boolean completionConditionFulfilled,
          final List<String> activateElementsCollection) {

    if (!activateElementsCollection.isEmpty() && completionConditionFulfilled) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_MSG_AHSP_CANNOT_ACTIVATE_ELEMENTS_WITH_COMPLETE_CONDITION_FULFILLED.formatted(
                  adHocSubProcessKey)));
    } else {
      return Either.right(activateElementsCollection);
    }
  }
}
