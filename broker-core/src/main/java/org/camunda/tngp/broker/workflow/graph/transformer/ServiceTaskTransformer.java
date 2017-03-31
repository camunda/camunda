package org.camunda.tngp.broker.workflow.graph.transformer;

import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableScope;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableServiceTask;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;
import org.camunda.tngp.broker.workflow.graph.transformer.metadata.TaskMetadataTransformer;

public class ServiceTaskTransformer implements BpmnElementTransformer<ServiceTask, ExecutableServiceTask>
{
    @Override
    public Class<ServiceTask> getType()
    {
        return ServiceTask.class;
    }

    @Override
    public void transform(ServiceTask modelElement, ExecutableServiceTask bpmnElement, ExecutableScope scope)
    {
        final ExtensionElements extensionElements = modelElement.getExtensionElements();
        ensureNotNull("extension elements", extensionElements);

        final TaskMetadata taskMetadata = TaskMetadataTransformer.transform(extensionElements);
        ensureNotNull("task metadata", taskMetadata);

        bpmnElement.setTaskMetadata(taskMetadata);
    }
}
