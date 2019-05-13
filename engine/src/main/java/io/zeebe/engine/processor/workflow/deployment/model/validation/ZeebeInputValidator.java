/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeInputValidator implements ModelElementValidator<ZeebeInput> {

  private final ZeebeExpressionValidator expressionValidator;

  public ZeebeInputValidator(ZeebeExpressionValidator expressionValidator) {
    this.expressionValidator = expressionValidator;
  }

  @Override
  public Class<ZeebeInput> getElementType() {
    return ZeebeInput.class;
  }

  @Override
  public void validate(ZeebeInput element, ValidationResultCollector validationResultCollector) {
    expressionValidator.validateJsonPath(element.getSource(), validationResultCollector);
    expressionValidator.validateJsonPath(element.getTarget(), validationResultCollector);
  }
}
