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

import static io.camunda.zeebe.model.bpmn.util.ModelUtil.validateExecutionListenersDefinitionForElement;

import io.camunda.zeebe.model.bpmn.instance.Gateway;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class GatewayValidator implements ModelElementValidator<Gateway> {

  @Override
  public Class<Gateway> getElementType() {
    return Gateway.class;
  }

  @Override
  public void validate(
      final Gateway element, final ValidationResultCollector validationResultCollector) {

    validateExecutionListenersDefinitionForElement(
        element,
        validationResultCollector,
        listeners -> {
          final boolean endExecutionListenersDefined =
              listeners.stream()
                  .map(ZeebeExecutionListener::getEventType)
                  .anyMatch(ZeebeExecutionListenerEventType.end::equals);
          if (endExecutionListenersDefined) {
            validationResultCollector.addError(
                0, "Execution listeners of type 'end' are not supported by gateway element");
          }
        });
  }
}
