package org.camunda.tngp.broker.workflow.graph.transformer;

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
        final TaskMetadata taskMetadata = TaskMetadataTransformer.transform(modelElement.getExtensionElements());

        bpmnElement.setTaskMetadata(taskMetadata);
    }
}
