package org.camunda.tngp.taskqueue.client.cmd;

public interface RecyclableCmd
{
    /**
     * resets the command for reuse
     */
    void reset();
}
