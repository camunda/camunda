package org.camunda.tngp.client.task;

public interface WaitStateResponse
{

    /**
     * Triggers completion of this wait state in the broker.
     */
    void complete();
}
