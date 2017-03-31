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
