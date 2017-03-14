package org.camunda.tngp.broker.workflow.graph.model.metadata;

public class TaskMetadata
{
    private String taskType;

    private TaskHeader[] headers;

    public TaskHeader[] getHeaders()
    {
        return headers;
    }

    public void setHeaders(TaskHeader[] headers)
    {
        this.headers = headers;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskype)
    {
        this.taskType = taskype;
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
