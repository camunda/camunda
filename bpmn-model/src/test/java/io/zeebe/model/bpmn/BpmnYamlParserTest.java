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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.*;
import org.junit.Test;

public class BpmnYamlParserTest
{
    private static final String INVALID_WORKFLOW = "/invalid_process.yaml";
    private static final String WORKFLOW_WITH_TASK_SEQUENCE = "/process.yaml";
    private static final String WORKFLOW_WITH_SPLIT = "/process-conditions.yaml";
    private static final String INVALID_WORKFLOW_WITH_SPLIT = "/invalid_process-conditions.yaml";

    @Test
    public void shouldReadFromFile() throws Exception
    {
        final URL resource = getClass().getResource(WORKFLOW_WITH_TASK_SEQUENCE);
        final File file = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlFile(file);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isFalse();
    }

    @Test
    public void shouldReadFromStream() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(WORKFLOW_WITH_TASK_SEQUENCE);

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(stream);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isFalse();
    }

    @Test
    public void shouldTransformTask() throws Exception
    {
        final WorkflowDefinition workflowDefinition = parseWorkflow(WORKFLOW_WITH_TASK_SEQUENCE);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("test"));
        assertThat(workflow).isNotNull();

        final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task1"));
        assertThat(serviceTask).isNotNull();

        final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
        assertThat(taskDefinition).isNotNull();
        assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
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

    @Test
    public void shouldTransformMultipleTasks() throws Exception
    {
        final WorkflowDefinition workflowDefinition = parseWorkflow(WORKFLOW_WITH_TASK_SEQUENCE);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("test"));
        assertThat(workflow).isNotNull();

        final ServiceTask task1 = workflow.findFlowElementById(wrapString("task1"));
        assertThat(task1).isNotNull();

        final TaskDefinition taskDefinition1 = task1.getTaskDefinition();
        assertThat(taskDefinition1).isNotNull();
        assertThat(taskDefinition1.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
        assertThat(taskDefinition1.getRetries()).isEqualTo(3);

        final ServiceTask task2 = workflow.findFlowElementById(wrapString("task2"));
        assertThat(task2).isNotNull();

        final TaskDefinition taskDefinition2 = task2.getTaskDefinition();
        assertThat(taskDefinition2).isNotNull();
        assertThat(taskDefinition2.getTypeAsBuffer()).isEqualTo(wrapString("bar"));
        assertThat(taskDefinition2.getRetries()).isEqualTo(5);

        assertThat(getFlows(workflow)).contains("task1 -> task2");
    }

    @Test
    public void shouldTransformExclusiveSplit() throws Exception
    {
        final WorkflowDefinition workflowDefinition = parseWorkflow(WORKFLOW_WITH_SPLIT);

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("test"));
        assertThat(workflow).isNotNull();

        final ServiceTask task1 = workflow.findFlowElementById(wrapString("task1"));
        assertThat(task1).isNotNull();
        assertThat(task1.getOutgoingSequenceFlows()).hasSize(1);

        final SequenceFlow flowToGateway = task1.getOutgoingSequenceFlows().get(0);
        final ExclusiveGateway exclusiveGateway = workflow.findFlowElementById(flowToGateway.getTargetNode().getIdAsBuffer());
        assertThat(exclusiveGateway).isNotNull();

        final List<SequenceFlow> outgoingSequenceFlowsWithConditions = exclusiveGateway.getOutgoingSequenceFlowsWithConditions();
        assertThat(outgoingSequenceFlowsWithConditions).hasSize(2);
        assertThat(outgoingSequenceFlowsWithConditions).allMatch(SequenceFlow::hasCondition);
        assertThat(outgoingSequenceFlowsWithConditions)
            .extracting(s -> s.getCondition().getExpression())
            .containsExactly("$.foo < 5", "$.foo >= 5 && $.foo < 10");

        final SequenceFlow defaultFlow = exclusiveGateway.getDefaultFlow();
        assertThat(defaultFlow).isNotNull();
        assertThat(defaultFlow.hasCondition()).isFalse();
        assertThat(defaultFlow.getTargetNode().getIdAsBuffer()).isEqualTo(wrapString("task4"));

        assertThat(getFlows(workflow))
            .contains("task1 -> split-task1")
            .contains("split-task1 -> task2")
            .contains("split-task1 -> task3")
            .contains("split-task1 -> task4")
            .contains("task2 -> task5")
            .contains("task3 -> task4")
            .doesNotContain("task2 -> task3")
            .doesNotContain("task4 -> task5");
    }

    @Test
    public void shouldReadInvalidFile() throws Exception
    {
        final URL resource = getClass().getResource(INVALID_WORKFLOW);
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlFile(bpmnFile);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString())
            .contains("BPMN process id is required.")
            .contains("A task definition must contain a 'type' attribute which specifies the type of the task.");
    }

    @Test
    public void shouldReadWorkflowWithInvalidExclusiveSplit() throws Exception
    {
        final URL resource = getClass().getResource(INVALID_WORKFLOW_WITH_SPLIT);
        final File yamlFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlFile(yamlFile);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString())
                .contains("A sequence flow on an exclusive gateway must have a condition, if it is not the default flow.");
    }

    private WorkflowDefinition parseWorkflow(String path)
    {
        final InputStream stream = getClass().getResourceAsStream(path);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(stream);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isFalse();

        return workflowDefinition;
    }

    private List<String> getFlows(Workflow workflow)
    {
        final ProcessImpl process = (ProcessImpl) workflow;

        return process.getSequenceFlows().stream()
            .map(s -> getFlowId(s.getSourceNode()) + " -> " + getFlowId(s.getTargetNode()))
            .collect(Collectors.toList());
    }

    private String getFlowId(FlowNode node)
    {
        return bufferAsString(node.getIdAsBuffer());
    }

}
