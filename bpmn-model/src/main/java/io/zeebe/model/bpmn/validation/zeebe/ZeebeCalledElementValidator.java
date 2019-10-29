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
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeCalledElementValidator implements ModelElementValidator<ZeebeCalledElement> {

  private static final String ERROR_MESSAGE =
      String.format(
          "Either '%s' or '%s' attribute must be present and not empty",
          ZeebeConstants.ATTRIBUTE_PROCESS_ID, ZeebeConstants.ATTRIBUTE_PROCESS_ID_EXPRESSION);

  @Override
  public Class<ZeebeCalledElement> getElementType() {
    return ZeebeCalledElement.class;
  }

  @Override
  public void validate(
      final ZeebeCalledElement element, final ValidationResultCollector validationResultCollector) {

    final Optional<String> processId =
        Optional.ofNullable(element.getProcessId()).filter(p -> !p.isEmpty());
    final Optional<String> processIdExpression =
        Optional.ofNullable(element.getProcessIdExpression()).filter(p -> !p.isEmpty());

    if (processId.isPresent() == processIdExpression.isPresent()) {
      validationResultCollector.addError(0, ERROR_MESSAGE);
    }
  }
}
