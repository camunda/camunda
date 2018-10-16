/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.model.validation;

import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import java.util.Collection;
import java.util.function.Function;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeIoMappingValidator implements ModelElementValidator<ZeebeIoMapping> {

  // TODO: move this somewhere else
  private static final String JSON_ROOT_PATH = "$";

  @Override
  public Class<ZeebeIoMapping> getElementType() {
    return ZeebeIoMapping.class;
  }

  @Override
  public void validate(
      ZeebeIoMapping element, ValidationResultCollector validationResultCollector) {

    final Collection<ZeebeInput> inputs = element.getInputs();
    final Collection<ZeebeOutput> outputs = element.getOutputs();

    if (inputs.size() > 1 && containsRootMapping(inputs, ZeebeInput::getTarget)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Invalid inputs: When using %s as target, no other input can be defined",
              JSON_ROOT_PATH));
    }

    if (outputs.size() > 1 && containsRootMapping(outputs, ZeebeOutput::getTarget)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Invalid outputs: When using %s as target, no other output can be defined",
              JSON_ROOT_PATH));
    }
  }

  private <T> boolean containsRootMapping(
      Collection<T> elements, Function<T, String> jsonPathExtractor) {
    return elements.stream().anyMatch(e -> JSON_ROOT_PATH.equals(jsonPathExtractor.apply(e)));
  }
}
