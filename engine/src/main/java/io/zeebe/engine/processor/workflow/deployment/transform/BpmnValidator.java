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
package io.zeebe.engine.processor.workflow.deployment.transform;

import io.zeebe.engine.processor.workflow.deployment.model.validation.ZeebeRuntimeValidators;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
import io.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.StringWriter;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class BpmnValidator {

  private final ValidationVisitor designTimeAspectValidator;
  private final ValidationVisitor runtimeAspectValidator;

  private final ValidationErrorFormatter formatter = new ValidationErrorFormatter();

  public BpmnValidator() {
    designTimeAspectValidator = new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);
    runtimeAspectValidator = new ValidationVisitor(ZeebeRuntimeValidators.VALIDATORS);
  }

  public String validate(BpmnModelInstance modelInstance) {
    designTimeAspectValidator.reset();
    runtimeAspectValidator.reset();

    final ModelWalker walker = new ModelWalker(modelInstance);
    walker.walk(designTimeAspectValidator);
    walker.walk(runtimeAspectValidator);

    final ValidationResults results1 = designTimeAspectValidator.getValidationResult();
    final ValidationResults results2 = runtimeAspectValidator.getValidationResult();

    if (results1.hasErrors() || results2.hasErrors()) {
      final StringWriter writer = new StringWriter();
      results1.write(writer, formatter);
      results2.write(writer, formatter);

      return writer.toString();
    } else {
      return null;
    }
  }
}
