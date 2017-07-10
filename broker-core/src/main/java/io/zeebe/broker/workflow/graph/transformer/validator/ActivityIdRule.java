/**
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
package io.zeebe.broker.workflow.graph.transformer.validator;

import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;

public class ActivityIdRule implements ModelElementValidator<FlowElement>
{
    @Override
    public Class<FlowElement> getElementType()
    {
        return FlowElement.class;
    }

    @Override
    public void validate(FlowElement flowElement, ValidationResultCollector validationResultCollector)
    {
        final String activityId = flowElement.getId();

        if (activityId == null || activityId.isEmpty())
        {
            validationResultCollector.addError(ValidationCodes.MISSING_ID, "Activity id is required.");
        }
        else if (activityId.length() > BpmnTransformer.ID_MAX_LENGTH)
        {
            validationResultCollector.addError(ValidationCodes.ID_TOO_LONG,
                    String.format("Activity id must not be longer than %d.", BpmnTransformer.ID_MAX_LENGTH));
        }
    }

}
