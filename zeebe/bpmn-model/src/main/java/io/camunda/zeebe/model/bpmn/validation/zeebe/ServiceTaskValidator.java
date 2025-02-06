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

import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ServiceTaskValidator implements ModelElementValidator<ServiceTask> {

  @Override
  public Class<ServiceTask> getElementType() {
    return ServiceTask.class;
  }

  @Override
  public void validate(
      final ServiceTask serviceTask, final ValidationResultCollector validationResultCollector) {
    if (!exactlyOneTaskDefinition(serviceTask)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Must have 'zeebe:%s' extension element", ZeebeConstants.ELEMENT_TASK_DEFINITION));
    }
  }

  private boolean exactlyOneTaskDefinition(final ServiceTask element) {
    final ExtensionElements extensionElements = element.getExtensionElements();
    if (extensionElements == null) {
      return false;
    }
    return extensionElements.getChildElementsByType(ZeebeTaskDefinition.class).size() == 1;
  }
}
