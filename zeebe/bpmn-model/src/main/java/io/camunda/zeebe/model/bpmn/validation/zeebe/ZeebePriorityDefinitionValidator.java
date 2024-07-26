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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePriorityDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebePriorityDefinitionValidator
    implements ModelElementValidator<ZeebePriorityDefinition> {

  private static final Long PRIORITY_LOWER_BOUND = 0L;
  private static final Long PRIORITY_UPPER_BOUND = 100L;

  @Override
  public Class<ZeebePriorityDefinition> getElementType() {
    return ZeebePriorityDefinition.class;
  }

  @Override
  public void validate(
      final ZeebePriorityDefinition zeebePriorityDefinition,
      final ValidationResultCollector validationResultCollector) {
    final String priority = zeebePriorityDefinition.getPriority();

    try {
      final int priorityValue = Integer.parseInt(priority);
      if (priorityValue < PRIORITY_LOWER_BOUND || priorityValue > PRIORITY_UPPER_BOUND) {
        validationResultCollector.addError(
            0,
            String.format("Priority must be a number between 0 and 100, but was '%s'", priority));
      }
    } catch (final NumberFormatException ignored) {
      /* Handled by previous runtime validation step */
    }
  }
}
