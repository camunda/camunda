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
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;

import io.zeebe.model.bpmn.instance.*;
import org.junit.Test;

public class BpmnIntegrationTest
{

    @Test
    public void shouldReadWorkflowFromFile() throws Exception
    {
        final URL resource = getClass().getResource("/process.bpmn");
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromFile(bpmnFile);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);

        final ServiceTask task = (ServiceTask) workflow.findFlowElementById(wrapString("task"));
        final TaskDefinition taskDefinition = task.getTaskDefinition();
        assertThat(taskDefinition).isNotNull();
        assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("task"));
        assertThat(taskDefinition.getRetries()).isEqualTo(3);

        final TaskHeaders taskHeaders = task.getTaskHeaders();
        assertThat(taskHeaders).isNotNull();
        assertThat(taskHeaders.asMap())
            .hasSize(2)
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");

        final InputOutputMapping inputOutputMapping = task.getInputOutputMapping();
        assertThat(inputOutputMapping).isNotNull();

        assertThat(inputOutputMapping.getInputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.a", "$.b");

        assertThat(inputOutputMapping.getOutputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.c", "$.d");
    }

    @Test
    public void shouldBuildWorkflow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .sequenceFlow()
            .serviceTask("task")
                .taskType("foo")
                .taskRetries(3)
                .taskHeader("foo", "f")
                .taskHeader("bar", "b")
                .input("$.a", "$.b")
                .output("$.c", "$.d")
                .done()
            .sequenceFlow()
            .endEvent("end")
            .done();

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);

        final ServiceTask task = (ServiceTask) workflow.findFlowElementById(wrapString("task"));
        final TaskDefinition taskDefinition = task.getTaskDefinition();
        assertThat(taskDefinition).isNotNull();
        assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
        assertThat(taskDefinition.getRetries()).isEqualTo(3);

        final TaskHeaders taskHeaders = task.getTaskHeaders();
        assertThat(taskHeaders).isNotNull();
        assertThat(taskHeaders.asMap())
            .hasSize(2)
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");

        final InputOutputMapping inputOutputMapping = task.getInputOutputMapping();
        assertThat(inputOutputMapping).isNotNull();

        assertThat(inputOutputMapping.getInputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.a", "$.b");

        assertThat(inputOutputMapping.getOutputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.c", "$.d");
    }

    @Test
    public void shouldBuildMinimalWorkflow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task")
                .taskType("task")
                .done()
            .endEvent("end")
            .done();

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));
    }

    @Test
    public void shouldBuildWorkflowClosureStyle()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", s -> s.taskType("task"))
            .endEvent("end")
            .done();

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        System.out.println(Bpmn.convertToString(workflowDefinition));
    }

    @Test
    public void shouldBuildMoreWorkflow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task1", s -> s.taskType("a"))
            .serviceTask("task2", s -> s.taskType("b"))
            .serviceTask("task3", s -> s.taskType("c"))
            .endEvent("end")
            .done();

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        System.out.println(Bpmn.convertToString(workflowDefinition));
    }

    @Test
    public void shouldReadWorkflowFromBuilder()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .sequenceFlow()
            .serviceTask("task")
                .taskType("foo")
                .taskRetries(3)
                .taskHeader("foo", "f")
                .taskHeader("bar", "b")
                .input("$.a", "$.b")
                .output("$.c", "$.d")
                .done()
            .sequenceFlow()
            .endEvent("end")
            .done();

        final String workflowAsString = Bpmn.convertToString(workflowDefinition);
        System.out.println(workflowAsString);

        final WorkflowDefinition deserializedWorkflowDefinition = Bpmn.readFromString(workflowAsString);

        assertThat(deserializedWorkflowDefinition).isNotNull();
        assertThat(deserializedWorkflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = deserializedWorkflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);

        final ServiceTask task = (ServiceTask) workflow.findFlowElementById(wrapString("task"));
        final TaskDefinition taskDefinition = task.getTaskDefinition();
        assertThat(taskDefinition).isNotNull();
        assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
        assertThat(taskDefinition.getRetries()).isEqualTo(3);

        final TaskHeaders taskHeaders = task.getTaskHeaders();
        assertThat(taskHeaders).isNotNull();
        assertThat(taskHeaders.asMap())
            .hasSize(2)
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");

        final InputOutputMapping inputOutputMapping = task.getInputOutputMapping();
        assertThat(inputOutputMapping).isNotNull();

        assertThat(inputOutputMapping.getInputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.a", "$.b");

        assertThat(inputOutputMapping.getOutputMappingsAsMap())
            .hasSize(1)
            .containsEntry("$.c", "$.d");
    }

}
