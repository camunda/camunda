package org.camunda.tngp.broker.transport.worker.spi;

import org.camunda.tngp.log.LogWriter;

public interface ResourceContext
{
    int getResourceId();
    String getResourceName();
    LogWriter getLogWriter();
}
