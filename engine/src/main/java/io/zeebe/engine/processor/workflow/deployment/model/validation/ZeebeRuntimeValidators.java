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

import java.util.ArrayList;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public class ZeebeRuntimeValidators {

  public static final Collection<ModelElementValidator<?>> VALIDATORS;

  static {
    final ZeebeExpressionValidator expressionValidator = new ZeebeExpressionValidator();

    VALIDATORS = new ArrayList<>();
    VALIDATORS.add(new ZeebeInputValidator(expressionValidator));
    VALIDATORS.add(new ZeebeOutputValidator(expressionValidator));
    VALIDATORS.add(new SequenceFlowValidator(expressionValidator));
    VALIDATORS.add(new ZeebeSubscriptionValidator(expressionValidator));
  }
}
