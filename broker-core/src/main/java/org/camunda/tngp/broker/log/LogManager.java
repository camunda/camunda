package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.log.cfg.LogCfg;
import org.camunda.tngp.logstreams.LogStream;

public interface LogManager
{
    void createLog(LogCfg config);

    LogStream getLogById(int id);
}