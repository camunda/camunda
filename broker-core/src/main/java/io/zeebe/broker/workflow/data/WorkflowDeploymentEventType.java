package io.zeebe.broker.workflow.data;

public enum WorkflowDeploymentEventType
{
    CREATE_DEPLOYMENT(0),
    DEPLOYMENT_CREATED(1),
    DEPLOYMENT_REJECTED(2);

    // don't change the ids because the stream processor use them for the index
    private final int id;

    WorkflowDeploymentEventType(int id)
    {
        this.id = id;
    }

    public int id()
    {
        return id;
    }
}
