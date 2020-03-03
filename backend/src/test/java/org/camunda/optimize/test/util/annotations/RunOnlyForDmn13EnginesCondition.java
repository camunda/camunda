/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.annotations;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.EngineVersionChecker.isVersionSupported;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

@Slf4j
public class RunOnlyForDmn13EnginesCondition implements ExecutionCondition {

  private static List<String> supportedEngines = new ArrayList<>();

  static {
    supportedEngines.add("7.10.17");
    supportedEngines.add("7.11.11");
    supportedEngines.add("7.12.4");
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
    Optional<RunOnlyForDmn13Engines> annotation = findAnnotation(context.getElement(), RunOnlyForDmn13Engines.class);
    if (annotation.isPresent()) {
      final String currentEngineVersion = System.getProperty("camunda.engine.version");
      if (currentEngineVersion == null) {
        log.warn(
          "Could not extract engine version! Running the DMN tests blindly. Be aware that the engine needs to support" +
            " DMN 1.3 for the tests to be able to complete successfully!");
        return ConditionEvaluationResult.enabled(
          "Could not extract DMN version of engine. Fingers crossed that it works. Continuing test!"
        );
      } else if (isVersionSupported(currentEngineVersion, supportedEngines)) {
        return ConditionEvaluationResult.enabled(String.format(
          "Dmn 1.3 is supported for engine '%s'. Continuing test!",
          currentEngineVersion
        ));
      } else {
        return ConditionEvaluationResult.disabled(String.format(
          "Engine version '%s' does not support Dmn 1.3. Skipping tests!",
          currentEngineVersion
        ));
      }
    }
    return ConditionEvaluationResult.enabled("No RunOnlyForDmn13Engines annotation found. Continuing test.");
  }
}
