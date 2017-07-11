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

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_DEFINITION_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADERS_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_KEY_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_VALUE_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_RETRIES_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_TYPE_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ServiceTaskRule implements ModelElementValidator<ServiceTask>
{

    @Override
    public Class<ServiceTask> getElementType()
    {
        return ServiceTask.class;
    }

    @Override
    public void validate(ServiceTask serviceTask, ValidationResultCollector validationResultCollector)
    {
        final ExtensionElements extensionElements = serviceTask.getExtensionElements();
        ModelElementInstance taskDefinition = null;
        ModelElementInstance taskHeaders = null;

        if (extensionElements != null)
        {
            taskDefinition = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);
            taskHeaders = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_HEADERS_ELEMENT);
        }

        if (taskDefinition == null)
        {
            validationResultCollector.addError(ValidationCodes.NO_TASK_DEFINITION,
                    String.format("A service task must contain a '%s' extension element.", TASK_DEFINITION_ELEMENT));
        }
        else
        {
            validateTaskDefinition(validationResultCollector, taskDefinition);
        }

        if (taskHeaders != null)
        {
            validateTaskHeaders(validationResultCollector, taskHeaders);
        }
    }

    private void validateTaskDefinition(ValidationResultCollector validationResultCollector, ModelElementInstance taskDefinition)
    {
        final String taskType = taskDefinition.getAttributeValue(TASK_TYPE_ATTRIBUTE);
        if (taskType == null || taskType.isEmpty())
        {
            validationResultCollector.addError(ValidationCodes.NO_TASK_TYPE,
                    String.format("A task definition must contain a '%s' attribute which specifies the type of the task.", TASK_TYPE_ATTRIBUTE));
        }

        final String taskRetries = taskDefinition.getAttributeValue(TASK_RETRIES_ATTRIBUTE);
        if (taskRetries != null && !taskRetries.isEmpty())
        {
            try
            {
                Integer.parseInt(taskRetries);
            }
            catch (NumberFormatException e)
            {
                validationResultCollector.addError(ValidationCodes.INVALID_TASK_RETRIES,
                        String.format("Invalid task retries in '%s' attribute. Expect number but found '%s'.", TASK_RETRIES_ATTRIBUTE, taskRetries));
            }
        }
    }

    private void validateTaskHeaders(ValidationResultCollector validationResultCollector, ModelElementInstance taskHeaders)
    {
        final List<DomElement> taskHeaderElements = taskHeaders.getDomElement().getChildElementsByNameNs(ZEEBE_NAMESPACE, TASK_HEADER_ELEMENT);

        taskHeaderElements.forEach(element ->
        {
            final String key = element.getAttribute(TASK_HEADER_KEY_ATTRIBUTE);
            final String value = element.getAttribute(TASK_HEADER_VALUE_ATTRIBUTE);

            if (key == null || key.isEmpty())
            {
                validationResultCollector.addError(ValidationCodes.NO_TASK_HEADER_KEY,
                        String.format("A task header must contain a '%s' attribute.", TASK_HEADER_KEY_ATTRIBUTE));
            }
            if (value == null || value.isEmpty())
            {
                validationResultCollector.addError(ValidationCodes.NO_TASK_HEADER_VALUE,
                        String.format("A task header must contain a '%s' attribute.", TASK_HEADER_VALUE_ATTRIBUTE));
            }
        });
    }

}
