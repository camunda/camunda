package org.camunda.tngp.broker.workflow.graph.model.metadata;

import org.agrona.DirectBuffer;

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

    public void setTaskType(DirectBuffer taskype)
    {
        this.taskType = taskype;
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
        private String key;
        private String value;

        public TaskHeader(String key, String value)
        {
            this.key = key;
            this.value = value;
        }

        public String getKey()
        {
            return key;
        }

        public void setkey(String name)
        {
            this.key = name;
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
