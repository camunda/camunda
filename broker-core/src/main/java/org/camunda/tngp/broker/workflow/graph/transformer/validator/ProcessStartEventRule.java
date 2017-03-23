/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.workflow.graph.transformer.validator;

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
