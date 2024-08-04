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

import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class InclusiveGatewayValidator implements ModelElementValidator<InclusiveGateway> {

  @Override
  public Class<InclusiveGateway> getElementType() {
    return InclusiveGateway.class;
  }

  @Override
  public void validate(
      final InclusiveGateway element, final ValidationResultCollector validationResultCollector) {

    final SequenceFlow defaultFlow = element.getDefault();
    if (defaultFlow != null) {
      if (defaultFlow.getSource() != element) {
        validationResultCollector.addError(0, "Default flow must start at gateway");
      }
    }
  }
}
