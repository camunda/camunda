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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;

import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.Test;

public class BpmnValidationTest
{

    @Test
    public void shouldValidateBpmnFile() throws Exception
    {
        final URL resource = getClass().getResource("/invalid_process.bpmn");
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlFile(bpmnFile);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("[ERROR] [line:4] (bpmn:startEvent) Activity id is required.");
    }

    @Test
    public void testMissingStartEvent()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("The process must contain at least one none start event.");
    }

    @Test
    public void testMissingActivityId()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("")
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("Activity id is required.");
    }

    @Test
    public void testMissingTaskDefinition()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask()
                .done()
            .endEvent()
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("A service task must contain a 'taskDefinition' extension element.");
    }

    @Test
    public void testMissingTaskType()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask()
                .taskRetries(3)
                .done()
            .endEvent()
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("A task definition must contain a 'type' attribute which specifies the type of the task.");
    }

    @Test
    public void testProhibitedInputOutputMapping()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask()
                .taskType("test")
                    .input("$.*", "$.foo")
                    .output("$.bar", "$.a[0,1]")
                .done()
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("Source mapping: JSON path '$.*' contains prohibited expression");
        assertThat(validationResult.toString()).contains("Target mapping: JSON path '$.a[0,1]' contains prohibited expression");
    }

    @Test
    public void testInvalidInputOutputMapping()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask()
                .taskType("test")
                    .input("foo", "$")
                    .output("bar", "$")
                .done()
            .done();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);

        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString()).contains("JSON path query 'foo' is not valid!");
        assertThat(validationResult.toString()).contains("JSON path query 'bar' is not valid!");
    }

}
