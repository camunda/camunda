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
