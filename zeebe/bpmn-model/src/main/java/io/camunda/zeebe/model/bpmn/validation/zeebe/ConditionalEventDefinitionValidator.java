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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.Condition;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConditionVariable;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ConditionalEventDefinitionValidator
    implements ModelElementValidator<ConditionalEventDefinition> {

  private static final List<String> VALID_VARIABLE_EVENTS = Arrays.asList("create", "update");

  @Override
  public Class<ConditionalEventDefinition> getElementType() {
    return ConditionalEventDefinition.class;
  }

  @Override
  public void validate(
      final ConditionalEventDefinition element,
      final ValidationResultCollector validationResultCollector) {

    // Validate that conditional events are only on supported event types
    validateEventContext(element, validationResultCollector);

    // Validate that a condition is present
    final Condition condition = element.getCondition();
    if (condition == null) {
      validationResultCollector.addError(0, "Must have a condition");
      return;
    }

    // Validate condition expression syntax
    final String conditionExpression = condition.getTextContent();
    if (conditionExpression == null || conditionExpression.trim().isEmpty()) {
      validationResultCollector.addError(0, "Condition expression must not be empty");
    } else {
      validateConditionSyntax(conditionExpression, validationResultCollector);
    }

    // Validate zeebe:conditionVariable extension if present
    validateConditionVariable(element, validationResultCollector);
  }

  private void validateEventContext(
      final ConditionalEventDefinition element,
      final ValidationResultCollector validationResultCollector) {
    final ModelElementInstance parentElement = element.getParentElement();

    if (parentElement instanceof BoundaryEvent
        || parentElement instanceof IntermediateCatchEvent) {
      // Valid: boundary events and intermediate catch events
      return;
    }

    if (parentElement instanceof StartEvent) {
      final StartEvent startEvent = (StartEvent) parentElement;
      final ModelElementInstance startEventParent = startEvent.getParentElement();

      if (startEventParent instanceof SubProcess) {
        final SubProcess subProcess = (SubProcess) startEventParent;
        if (subProcess.triggeredByEvent()) {
          // Valid: event subprocess start event
          return;
        }
      } else {
        // Valid: root-level start event (process start event)
        return;
      }
    }

    validationResultCollector.addError(
        0,
        "Conditional events are only supported on boundary events, intermediate catch events, event subprocess start events, and root-level start events");
  }

  private void validateConditionSyntax(
      final String expression, final ValidationResultCollector validationResultCollector) {
    // Basic syntax validation - check for common malformed patterns
    final String trimmed = expression.trim();

    // Check for obvious syntax errors
    if (trimmed.contains(">>>") || trimmed.contains("<<<")) {
      validationResultCollector.addError(0, "Invalid condition expression syntax");
      return;
    }

    // Check for unbalanced parentheses
    int parenCount = 0;
    for (char c : trimmed.toCharArray()) {
      if (c == '(') {
        parenCount++;
      } else if (c == ')') {
        parenCount--;
        if (parenCount < 0) {
          validationResultCollector.addError(
              0, "Invalid condition expression: unbalanced parentheses");
          return;
        }
      }
    }
    if (parenCount != 0) {
      validationResultCollector.addError(0, "Invalid condition expression: unbalanced parentheses");
    }
  }

  private void validateConditionVariable(
      final ConditionalEventDefinition element,
      final ValidationResultCollector validationResultCollector) {
    final ModelElementInstance parentElement = element.getParentElement();
    if (parentElement != null) {
      final ExtensionElements extensionElements =
          (ExtensionElements)
              parentElement.getUniqueChildElementByType(ExtensionElements.class);

      if (extensionElements != null) {
        extensionElements.getChildElementsByType(ZeebeConditionVariable.class).stream()
            .findFirst()
            .ifPresent(
                conditionVariable ->
                    validateVariableEvents(conditionVariable, validationResultCollector));
      }
    }
  }

  private void validateVariableEvents(
      final ZeebeConditionVariable conditionVariable,
      final ValidationResultCollector validationResultCollector) {
    final String variableEvents = conditionVariable.getVariableEvents();
    if (variableEvents != null && !variableEvents.trim().isEmpty()) {
      final String[] events = variableEvents.split(",");
      for (final String event : events) {
        final String trimmedEvent = event.trim();
        if (!trimmedEvent.isEmpty() && !VALID_VARIABLE_EVENTS.contains(trimmedEvent)) {
          validationResultCollector.addError(
              0,
              "Invalid variable event '"
                  + trimmedEvent
                  + "'. Valid values are: "
                  + String.join(", ", VALID_VARIABLE_EVENTS));
        }
      }
    }
  }
}
