/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.impl.validation.nodes.task;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.metadata.InputOutputMappingImpl;
import io.zeebe.model.bpmn.instance.OutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

public class InputOutputMappingValidator {
  public static final String OUTPUT_BEHAVIOR_IS_NOT_SUPPORTED_MSG =
      "Output behavior '%s' is not supported. Valid values are %s.";

  private static final String PROHIBITED_EXPRESSIONS_REGEX = "(\\.\\*)|(\\[.*,.*\\])";
  private static final Pattern PROHIBITED_EXPRESSIONS =
      Pattern.compile(PROHIBITED_EXPRESSIONS_REGEX);

  public void validate(ErrorCollector validationResult, InputOutputMappingImpl inputOutputMapping) {
    validateOutputBehavior(
        validationResult, inputOutputMapping, inputOutputMapping.getOutputBehaviorString());

    validateMappingExpressions(
        validationResult, inputOutputMapping, inputOutputMapping.getInputMappingsAsMap());
    validateMappingExpressions(
        validationResult, inputOutputMapping, inputOutputMapping.getOutputMappingsAsMap());
  }

  private void validateOutputBehavior(
      ErrorCollector validationResult, InputOutputMappingImpl element, String outputBehavior) {
    OutputBehavior behavior = null;
    try {
      behavior = OutputBehavior.valueOf(outputBehavior.toUpperCase());
    } catch (Exception exception) {
      validationResult.addError(
          element,
          String.format(
              OUTPUT_BEHAVIOR_IS_NOT_SUPPORTED_MSG,
              outputBehavior,
              Arrays.toString(OutputBehavior.values())));
    }

    final boolean hasOutputMapping = element.getOutputMappingsAsMap().size() > 0;
    if (behavior == OutputBehavior.NONE && hasOutputMapping) {
      validationResult.addError(
          element,
          String.format(
              "Output behavior '%s' is not supported in combination with output mappings.",
              outputBehavior));
    }
  }

  private void validateMappingExpressions(
      ErrorCollector validationResult,
      InputOutputMappingImpl element,
      Map<String, String> mappings) {
    for (Map.Entry<String, String> mapping : mappings.entrySet()) {
      final String source = mapping.getKey();
      final String target = mapping.getValue();

      if (PROHIBITED_EXPRESSIONS.matcher(source).find()) {
        validationResult.addError(
            element,
            String.format(
                "Source mapping: JSON path '%s' contains prohibited expression (for example $.* or $.(foo|bar)).",
                source));
      }

      if (PROHIBITED_EXPRESSIONS.matcher(target).find()) {
        validationResult.addError(
            element,
            String.format(
                "Target mapping: JSON path '%s' contains prohibited expression (for example $.* or $.(foo|bar)).",
                target));
      }

      if (mappings.size() > 1 && target.equals(Mapping.JSON_ROOT_PATH)) {
        validationResult.addError(
            element,
            "Target mapping: root mapping is not allowed because it would override other mapping.");
      }
    }
  }
}
