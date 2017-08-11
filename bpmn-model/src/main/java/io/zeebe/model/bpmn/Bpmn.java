/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn;

import java.io.*;

import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.impl.BpmnParser;
import io.zeebe.model.bpmn.impl.BpmnTransformer;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class Bpmn
{
    private static BpmnParser parser = new BpmnParser();
    private static BpmnTransformer transformer = new BpmnTransformer();


    public static BpmnBuilder createExecutableWorkflow(String bpmnProcessId)
    {
        return new BpmnBuilder(transformer, bpmnProcessId);
    }

    public static WorkflowDefinition readFromFile(File file)
    {
        final DefinitionsImpl definitions = parser.readFromFile(file);
        // TODO validate workflow
        final WorkflowDefinition workflowDefinition = transformer.transform(definitions);

        return workflowDefinition;
    }

    public static WorkflowDefinition readFromStream(InputStream stream)
    {
        final DefinitionsImpl definitions = parser.readFromStream(stream);
        // TODO validate workflow
        final WorkflowDefinition workflowDefinition = transformer.transform(definitions);

        return workflowDefinition;
    }

    public static WorkflowDefinition readFromString(String workflow)
    {
        return readFromStream(new ByteArrayInputStream(workflow.getBytes()));
    }

    public static String convertToString(WorkflowDefinition definition)
    {
        if (definition instanceof DefinitionsImpl)
        {
            return parser.convertToString((DefinitionsImpl) definition);
        }
        else
        {
            throw new RuntimeException("not supported");
        }
    }

}
