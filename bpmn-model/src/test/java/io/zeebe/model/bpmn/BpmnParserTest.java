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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import io.zeebe.model.bpmn.instance.*;
import org.assertj.core.util.Files;
import org.junit.Test;

public class BpmnParserTest
{
    private static final String BPMN_FILE = "/process.bpmn";

    @Test
    public void shouldReadFromFile() throws Exception
    {
        final URL resource = getClass().getResource(BPMN_FILE);
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromFile(bpmnFile);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);
    }

    @Test
    public void shouldReadFromStream() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);

        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);
    }

    @Test
    public void shouldReadFromString() throws Exception
    {
        final URL resource = getClass().getResource(BPMN_FILE);
        final File bpmnFile = new File(resource.toURI());
        final String workflow = Files.contentOf(bpmnFile, UTF_8);

        final WorkflowDefinition workflowDefinition = Bpmn.readFromString(workflow);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);
    }

    @Test
    public void shouldTransformValidWorkflow() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        assertThat(workflowDefinition).isNotNull();

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isFalse();
    }

    @Test
    public void shouldTransformWorkflow() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));
    }

    @Test
    public void shouldTransformStartEvent() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
    }

    @Test
    public void shouldTransformSequenceFlows() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
        final StartEvent initialStartEvent = workflow.getInitialStartEvent();

        assertThat(initialStartEvent.getOutgoingSequenceFlows()).hasSize(1);

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);

        assertThat(targetElement.getOutgoingSequenceFlows()).hasSize(1);

        final FlowNode nextElement = targetElement.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(nextElement).isNotNull();
        assertThat(nextElement.getIdAsBuffer()).isEqualTo(wrapString("end"));

        assertThat(nextElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(nextElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(targetElement);
        assertThat(nextElement.getOutgoingSequenceFlows()).isEmpty();
    }

    @Test
    public void shouldTransformServiceTask() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromStream(stream);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

        final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
        final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
        assertThat(taskDefinition).isNotNull();
        assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("task"));
        assertThat(taskDefinition.getRetries()).isEqualTo(3);

        final TaskHeaders taskHeaders = serviceTask.getTaskHeaders();
        assertThat(taskHeaders).isNotNull();
        assertThat(taskHeaders.asMap())
            .hasSize(2)
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");

        final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
        assertThat(inputOutputMapping).isNotNull();

        assertThat(inputOutputMapping.getInputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.a", "$.b");

        assertThat(inputOutputMapping.getOutputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.c", "$.d");
    }

}
