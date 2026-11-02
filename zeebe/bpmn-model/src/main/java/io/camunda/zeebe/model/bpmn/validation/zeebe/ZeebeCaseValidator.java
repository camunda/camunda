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

import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeCaseValidator implements ModelElementValidator<ZeebeCase> {

  @Override
  public Class<ZeebeCase> getElementType() {
    return ZeebeCase.class;
  }

  @Override
  public void validate(
      final ZeebeCase element, final ValidationResultCollector validationResultCollector) {
    final ModelElementInstance parent = element.getParentElement();
    final boolean inExtensionElements = parent instanceof ExtensionElements;
    final ModelElementInstance owner = inExtensionElements ? parent.getParentElement() : parent;
    if (!inExtensionElements || !(owner instanceof Process)) {
      validationResultCollector.addError(
          0,
          String.format(
              "The case marker 'zeebe:case' is only allowed on a process, but was found on '%s'",
              owner.getElementType().getTypeName()));
      return;
    }

    final String title = element.getTitle();
    if (title == null || title.trim().isEmpty()) {
      validationResultCollector.addError(0, "Attribute 'title' must be present and not empty");
      return;
    }

    if (title.startsWith("=")) {
      validationResultCollector.addError(
          0, "Attribute 'title' must be a variable name, not an expression");
    }
  }
}
