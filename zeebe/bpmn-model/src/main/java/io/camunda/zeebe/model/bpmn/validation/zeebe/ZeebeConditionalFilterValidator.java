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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConditionalFilter;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeConditionalFilterValidator
    implements ModelElementValidator<ZeebeConditionalFilter> {

  private static final List<String> VALID_VARIABLE_EVENTS = Arrays.asList("create", "update");

  @Override
  public Class<ZeebeConditionalFilter> getElementType() {
    return ZeebeConditionalFilter.class;
  }

  @Override
  public void validate(
      final ZeebeConditionalFilter element,
      final ValidationResultCollector validationResultCollector) {
    final String variableEvents = element.getVariableEvents();

    if (variableEvents != null && !variableEvents.trim().isEmpty()) {
      final String[] events = variableEvents.split(",");
      for (final String event : events) {
        final String trimmedEvent = event.trim();
        if (!trimmedEvent.isEmpty() && !VALID_VARIABLE_EVENTS.contains(trimmedEvent)) {
          validationResultCollector.addError(
              0,
              String.format(
                  "Variable event '%s' is not valid. Must be one of: %s or a comma-separated list of both.",
                  trimmedEvent, String.join(", ", VALID_VARIABLE_EVENTS)));
        }
      }
    }
  }
}
