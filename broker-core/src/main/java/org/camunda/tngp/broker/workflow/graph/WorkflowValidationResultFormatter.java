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
package org.camunda.tngp.broker.workflow.graph;

import java.io.StringWriter;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultFormatter;

/**
 * Format the validation result using the pattern:
 *
 * <pre>
 * elementId: ERROR [errorCode] message
 * elementId: WARNING [errorCode] message
 * </pre>
 */
public class WorkflowValidationResultFormatter implements ValidationResultFormatter
{

    @Override
    public void formatResult(StringWriter writer, ValidationResult result)
    {
        final String errorType = result.getType().name();
        final int errorCode = result.getCode();
        final String errorMessage = result.getMessage();

        writer
            .append(": ")
            .append(errorType)
            .append(" [")
            .append(String.valueOf(errorCode))
            .append("] ")
            .append(errorMessage)
            .append((char) Character.LINE_SEPARATOR);
    }

    @Override
    public void formatElement(StringWriter writer, ModelElementInstance element)
    {
        String id = element.getAttributeValue("id");

        if (id == null)
        {
            id = "(null)";
        }

        writer.append(id);
    }

}
