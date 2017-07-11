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
package io.zeebe.broker.workflow.graph;

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
