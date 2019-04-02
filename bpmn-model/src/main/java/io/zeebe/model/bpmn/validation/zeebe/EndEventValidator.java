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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class EndEventValidator implements ModelElementValidator<EndEvent> {

  @Override
  public Class<EndEvent> getElementType() {
    return EndEvent.class;
  }

  @Override
  public void validate(EndEvent element, ValidationResultCollector validationResultCollector) {
    if (!element.getEventDefinitions().isEmpty()) {
      validationResultCollector.addError(0, "Must be a none end event");
    }

    if (!element.getOutgoing().isEmpty()) {
      validationResultCollector.addError(
          0, "End events must not have outgoing sequence flows to other elements.");
    }
  }
}
