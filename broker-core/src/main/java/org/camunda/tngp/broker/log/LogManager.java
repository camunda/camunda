package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.log.cfg.LogCfg;
import org.camunda.tngp.log.Log;

public interface LogManager
{
    void createLog(LogCfg config);

    Log getLogById(int id);
}