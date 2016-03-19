package org.camunda.tngp.log.appender;

public interface LogAppenderCmd
{
    void execute(LogAppender appender);
}
