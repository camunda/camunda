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

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class CompensationTaskValidator implements ModelElementValidator<Task> {

  @Override
  public Class<Task> getElementType() {
    return Task.class;
  }

  @Override
  public void validate(
      final Task element, final ValidationResultCollector validationResultCollector) {
    IdentifiableBpmnElementValidator.validate(element, validationResultCollector);

    final String isForCompensation =
        element.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_IS_FOR_COMPENSATION);
    if (Boolean.parseBoolean(isForCompensation)) {

      if (!element.getIncoming().isEmpty()) {
        validationResultCollector.addError(
            0, "A compensation handler should have no incoming sequence flows");
      }

      if (!element.getOutgoing().isEmpty()) {
        validationResultCollector.addError(
            0, "A compensation handler should have no outgoing sequence flows");
      }

      if (element.getBoundaryEvents().count() > 0) {
        validationResultCollector.addError(
            0, "A compensation handler should have no boundary events");
      }
    }
  }
}
