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

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_DEFINITION_ELEMENT;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_TYPE_ATTRIBUTE;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TNGP_NAMESPACE;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
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

        if (extensionElements != null)
        {
            taskDefinition = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, TASK_DEFINITION_ELEMENT);
        }

        if (taskDefinition == null)
        {
            validationResultCollector.addError(ValidationCodes.NO_TASK_DEFINITION,
                    String.format("A service task must contain a '%s' extension element.", TASK_DEFINITION_ELEMENT));
        }
        else
        {
            final String taskType = taskDefinition.getAttributeValue(TASK_TYPE_ATTRIBUTE);

            if (taskType == null || taskType.isEmpty())
            {
                validationResultCollector.addError(ValidationCodes.NO_TASK_TYPE,
                        String.format("A task definition must contain a '%s' attribute which specifies the type of the task.", TASK_TYPE_ATTRIBUTE));
            }
        }
    }

}
