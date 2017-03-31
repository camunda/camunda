package org.camunda.tngp.broker.workflow.graph.model.metadata;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferUtil;

public class TaskMetadata
{
    private DirectBuffer taskType;
    private int retries;

    private TaskHeader[] headers;

    public TaskHeader[] getHeaders()
    {
        return headers;
    }

    public void setHeaders(TaskHeader[] headers)
    {
        this.headers = headers;
    }

    public DirectBuffer getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskype)
    {
        this.taskType = BufferUtil.wrapString(taskype);
    }

    public int getRetries()
    {
        return retries;
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }

    public static class TaskHeader
    {
        private String name;
        private String value;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

}
