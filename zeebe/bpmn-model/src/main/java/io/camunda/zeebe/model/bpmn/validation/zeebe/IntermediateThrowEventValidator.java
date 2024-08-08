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

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION;

import io.camunda.zeebe.model.bpmn.impl.QueryImpl;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class IntermediateThrowEventValidator
    implements ModelElementValidator<IntermediateThrowEvent> {

  @Override
  public Class<IntermediateThrowEvent> getElementType() {
    return IntermediateThrowEvent.class;
  }

  @Override
  public void validate(
      final IntermediateThrowEvent element,
      final ValidationResultCollector validationResultCollector) {

    final Optional<CompensateEventDefinition> compensateEventDefinitionOpt =
        getEventDefinition(element);

    if (compensateEventDefinitionOpt.isPresent()) {
      final CompensateEventDefinition definition = compensateEventDefinitionOpt.get();
      final String waitForCompletion =
          definition.getAttributeValue(BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION);
      if (waitForCompletion != null && !Boolean.parseBoolean(waitForCompletion)) {
        validationResultCollector.addError(
            0,
            "A compensation intermediate throwing event waitForCompletion attribute must be true or not present");
      }
    }
  }

  private Optional<CompensateEventDefinition> getEventDefinition(
      final IntermediateThrowEvent event) {
    return new QueryImpl<>(event.getEventDefinitions())
        .filterByType(CompensateEventDefinition.class)
        .findSingleResult();
  }
}
