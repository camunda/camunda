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

import java.io.File;
import java.io.InputStream;

import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.agrona.DirectBuffer;

public class Bpmn
{

    private static final BpmnModelApi INSTANCE = new BpmnModelApi();

    public static BpmnBuilder createExecutableWorkflow(String bpmnProcessId)
    {
        return INSTANCE.createExecutableWorkflow(bpmnProcessId);
    }

    public static WorkflowDefinition readFromFile(File file)
    {
        return INSTANCE.readFromFile(file);
    }

    public static WorkflowDefinition readFromStream(InputStream stream)
    {
        return INSTANCE.readFromStream(stream);
    }

    public static WorkflowDefinition readFromBuffer(DirectBuffer buffer)
    {
        return INSTANCE.readFromBuffer(buffer);
    }

    public static WorkflowDefinition readFromString(String workflow)
    {
        return INSTANCE.readFromString(workflow);
    }

    public static WorkflowDefinition readFromYamlFile(File file)
    {
        return INSTANCE.readFromYamlFile(file);
    }

    public static WorkflowDefinition readFromYamlStream(InputStream stream)
    {
        return INSTANCE.readFromYamlStream(stream);
    }

    public static ValidationResult validate(WorkflowDefinition definition)
    {
        return INSTANCE.validate(definition);
    }

    public static String convertToString(WorkflowDefinition definition)
    {
        return INSTANCE.convertToString(definition);
    }

}
