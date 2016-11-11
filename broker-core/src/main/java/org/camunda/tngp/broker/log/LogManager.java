package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.log.cfg.LogCfg;

public interface LogManager
{
    void createLog(LogCfg config);
}