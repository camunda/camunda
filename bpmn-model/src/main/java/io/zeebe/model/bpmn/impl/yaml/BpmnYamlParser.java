package io.zeebe.model.bpmn.impl.yaml;

import java.io.*;
import java.util.Map.Entry;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.builder.BpmnServiceTaskBuilder;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.yaml.snakeyaml.Yaml;

public class BpmnYamlParser
{
    private final Yaml parser = new Yaml();

    public WorkflowDefinition readFromFile(File file)
    {
        try
        {
            return readFromStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    public WorkflowDefinition readFromStream(InputStream inputStream)
    {
        final YamlDefinitionImpl definition = parser.loadAs(inputStream, YamlDefinitionImpl.class);

        return createWorkflow(definition);
    }

    private WorkflowDefinition createWorkflow(final YamlDefinitionImpl definition)
    {
        // only simple workflow with start event, service tasks* and end event
        final BpmnBuilder builder = Bpmn.createExecutableWorkflow(definition.getName())
                .startEvent();

        for (YamlTask task : definition.getTasks())
        {
            addServiceTask(builder, task);
        }

        return builder.endEvent().done();
    }

    private void addServiceTask(final BpmnBuilder builder, YamlTask task)
    {
        final String id = task.getId();
        final String taskType = task.getType();
        final Integer taskRetries = task.getRetries();

        final BpmnServiceTaskBuilder serviceTaskBuilder = builder
            .serviceTask(id)
            .taskType(taskType)
            .taskRetries(taskRetries);

        for (Entry<String, String> header : task.getHeaders().entrySet())
        {
            serviceTaskBuilder.taskHeader(header.getKey(), header.getValue());
        }

        for (YamlMapping inputMapping : task.getInputs())
        {
            serviceTaskBuilder.input(inputMapping.getSource(), inputMapping.getTarget());
        }

        for (YamlMapping outputMapping : task.getOutputs())
        {
            serviceTaskBuilder.output(outputMapping.getSource(), outputMapping.getTarget());
        }

        serviceTaskBuilder.done();
    }

}
