package io.zeebe.broker.workflow.graph.model;

import io.zeebe.broker.workflow.graph.model.metadata.IOMapping;
import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata;

public class ExecutableServiceTask extends ExecutableFlowNode
{
    private TaskMetadata taskMetadata;
    private IOMapping ioMapping;

    public TaskMetadata getTaskMetadata()
    {
        return taskMetadata;
    }

    public void setTaskMetadata(TaskMetadata taskMetadata)
    {
        this.taskMetadata = taskMetadata;
    }

    public IOMapping getIoMapping()
    {
        return ioMapping;
    }

    public void setIoMapping(IOMapping ioMapping)
    {
        this.ioMapping = ioMapping;
    }
}
