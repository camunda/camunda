package org.camunda.tngp.log.conductor;

import org.camunda.tngp.log.appender.LogAppender;
import org.camunda.tngp.log.appender.LogAppenderCmd;
import org.camunda.tngp.log.fs.AppendableLogSegment;

public class InitAppenderCmd implements LogAppenderCmd
{
    private final AppendableLogSegment initialLogSegment;

    public InitAppenderCmd(AppendableLogSegment appendableLogSegment)
    {
        this.initialLogSegment = appendableLogSegment;
    }

    @Override
    public void execute(LogAppender appender)
    {
        appender.init(initialLogSegment);
    }

}
