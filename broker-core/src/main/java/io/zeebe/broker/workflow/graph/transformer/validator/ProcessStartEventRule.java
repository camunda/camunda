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

import java.util.Collection;

import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ProcessStartEventRule implements ModelElementValidator<Process>
{

    @Override
    public Class<Process> getElementType()
    {
        return Process.class;
    }

    @Override
    public void validate(Process process, ValidationResultCollector validationResultCollector)
    {
        final Collection<StartEvent> startEvents = process.getChildElementsByType(StartEvent.class);

        int noneStartEventCount = 0;

        for (StartEvent startEvent : startEvents)
        {
            final Collection<EventDefinition> eventDefinitions = startEvent.getEventDefinitions();

            if (eventDefinitions.isEmpty())
            {
                noneStartEventCount += 1;
            }
            else
            {
                final String errorMessage = String.format("Ignore start event with id '%s'. Event type is not supported.", startEvent.getId());
                validationResultCollector.addWarning(ValidationCodes.NOT_SUPPORTED_START_EVENT, errorMessage);
            }
        }

        if (noneStartEventCount == 0)
        {
            validationResultCollector.addError(ValidationCodes.NO_START_EVENT, "The process must contain at least one none start event.");
        }
        else if (noneStartEventCount > 1)
        {
            validationResultCollector.addError(ValidationCodes.MORE_THAN_ONE_NONE_START_EVENT, "The process must not contain more than one none start event.");
        }
    }

}
