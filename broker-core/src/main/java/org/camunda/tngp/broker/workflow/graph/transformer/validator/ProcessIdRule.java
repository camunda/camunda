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

import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ProcessIdRule implements ModelElementValidator<Process>
{
    public static final int PROCESS_ID_MAX_LENGTH = 255;

    @Override
    public Class<Process> getElementType()
    {
        return Process.class;
    }

    @Override
    public void validate(Process process, ValidationResultCollector validationResultCollector)
    {
        final String processId = process.getId();

        if (processId == null || processId.isEmpty())
        {
            validationResultCollector.addError(ValidationCodes.NO_PROCESS_ID, "Process Id is required.");
        }
        else if (processId.length() > PROCESS_ID_MAX_LENGTH)
        {
            validationResultCollector.addError(ValidationCodes.PROCESS_ID_TOO_LONG, "Process Id must not be longer than " + PROCESS_ID_MAX_LENGTH + " chars.");
        }
    }

}
