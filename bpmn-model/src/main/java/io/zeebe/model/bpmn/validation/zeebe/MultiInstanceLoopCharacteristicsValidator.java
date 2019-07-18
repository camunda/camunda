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

import io.zeebe.model.bpmn.impl.ZeebeConstants;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class MultiInstanceLoopCharacteristicsValidator
    implements ModelElementValidator<MultiInstanceLoopCharacteristics> {

  @Override
  public Class<MultiInstanceLoopCharacteristics> getElementType() {
    return MultiInstanceLoopCharacteristics.class;
  }

  @Override
  public void validate(
      MultiInstanceLoopCharacteristics element,
      ValidationResultCollector validationResultCollector) {
    final ExtensionElements extensionElements = element.getExtensionElements();

    if (extensionElements == null
        || extensionElements.getChildElementsByType(ZeebeLoopCharacteristics.class).size() != 1) {
      validationResultCollector.addError(
          0,
          String.format(
              "Must have exactly one 'zeebe:%s' extension element",
              ZeebeConstants.ELEMENT_LOOP_CHARACTERISTICS));
    }
  }
}
