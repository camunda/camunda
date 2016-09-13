package org.camunda.tngp.client.task.impl;

@FunctionalInterface
public interface AcquisitionCmd
{

    void execute(TaskAcquisition acquisition);
}
