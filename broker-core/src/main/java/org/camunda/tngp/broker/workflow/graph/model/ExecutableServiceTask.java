package org.camunda.tngp.broker.workflow.graph.model;

import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;

public class ExecutableServiceTask extends ExecutableFlowNode
{
    private TaskMetadata taskMetadata;

    public TaskMetadata getTaskMetadata()
    {
        return taskMetadata;
    }

    public void setTaskMetadata(TaskMetadata taskMetadata)
    {
        this.taskMetadata = taskMetadata;
    }
}
