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
package io.zeebe.broker.workflow.graph.transformer.validator;

import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.MORE_THAN_ONE_EXECUTABLE_PROCESS;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.NO_EXECUTABLE_PROCESS;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ExecutableProcessRule implements ModelElementValidator<Definitions>
{

    @Override
    public Class<Definitions> getElementType()
    {
        return Definitions.class;
    }

    @Override
    public void validate(Definitions definitionsElement, ValidationResultCollector resultCollector)
    {
        final Collection<Process> processes = definitionsElement.getChildElementsByType(Process.class);

        int executableProcesses = 0;

        for (Process process : processes)
        {
            if (process.isExecutable())
            {
                executableProcesses += 1;
            }
        }

        if (executableProcesses == 0)
        {
            resultCollector.addError(NO_EXECUTABLE_PROCESS, "BPMN model must contain at least one executable process.");
        }
        else if (executableProcesses > 1)
        {
            resultCollector.addError(MORE_THAN_ONE_EXECUTABLE_PROCESS, "BPMN model must not contain more than one executable process.");
        }
    }

}
