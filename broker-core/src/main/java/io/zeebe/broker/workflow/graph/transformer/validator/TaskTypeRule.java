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

import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class TaskTypeRule implements ModelElementValidator<Task>
{

    @Override
    public Class<Task> getElementType()
    {
        return Task.class;
    }

    @Override
    public void validate(Task task, ValidationResultCollector validationResultCollector)
    {
        if (!(task instanceof ServiceTask))
        {
            validationResultCollector.addError(ValidationCodes.NOT_SUPPORTED_TASK_TYPE, "Not supported task type. Please use a service task instead.");
        }
    }

}
