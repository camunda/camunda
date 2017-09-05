package io.zeebe.model.bpmn;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import io.zeebe.model.bpmn.instance.*;
import org.junit.Test;

public class BpmnYamlParserTest
{
    private static final String YAML_FILE = "/process.yaml";
    private static final String INVALID_YAML_FILE = "/invalid_process.yaml";

    @Test
    public void shouldReadFromFile() throws Exception
    {
        final URL resource = getClass().getResource(YAML_FILE);
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
        final InputStream stream = getClass().getResourceAsStream(YAML_FILE);

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(stream);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isFalse();
    }

    @Test
    public void shouldTransformTask() throws Exception
    {
        final InputStream stream = getClass().getResourceAsStream(YAML_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(stream);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("test"));
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
        final InputStream stream = getClass().getResourceAsStream(YAML_FILE);
        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(stream);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("test"));
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
    }

    @Test
    public void shouldReadInvalidFile() throws Exception
    {
        final URL resource = getClass().getResource(INVALID_YAML_FILE);
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromYamlFile(bpmnFile);

        final ValidationResult validationResult = Bpmn.validate(workflowDefinition);
        assertThat(validationResult.hasErrors()).isTrue();
        assertThat(validationResult.toString())
            .contains("BPMN process id is required.")
            .contains("A task definition must contain a 'type' attribute which specifies the type of the task.");
    }

}
