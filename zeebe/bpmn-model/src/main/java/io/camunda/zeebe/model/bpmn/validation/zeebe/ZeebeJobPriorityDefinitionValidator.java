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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeJobPriorityDefinitionValidator
    implements ModelElementValidator<ZeebeJobPriorityDefinition> {

  @Override
  public Class<ZeebeJobPriorityDefinition> getElementType() {
    return ZeebeJobPriorityDefinition.class;
  }

  @Override
  public void validate(
      final ZeebeJobPriorityDefinition element,
      final ValidationResultCollector validationResultCollector) {
    final String priority = element.getPriority();
    if (priority.startsWith("=")) {
      // FEEL expression — syntax validation is deferred to the engine transformer
      return;
    }
    try {
      Integer.parseInt(priority);
    } catch (final NumberFormatException e) {
      validationResultCollector.addError(
          0,
          String.format(
              "Expected 'priority' to be a literal integer or a FEEL expression (starting with '='), but got '%s'.",
              priority));
    }
  }
}
