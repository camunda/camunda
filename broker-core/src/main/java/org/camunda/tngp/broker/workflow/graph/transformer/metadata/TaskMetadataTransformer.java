package org.camunda.tngp.broker.workflow.graph.transformer.metadata;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;

public class TaskMetadataTransformer
{
    public static TaskMetadata transform(ExtensionElements extensionElements)
    {
        final TaskMetadata taskMetadata = new TaskMetadata();

        // TODO transform task metadata

        return taskMetadata;
    }
}
