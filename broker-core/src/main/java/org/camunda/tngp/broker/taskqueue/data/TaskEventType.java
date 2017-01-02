package org.camunda.tngp.broker.taskqueue.data;

public enum TaskEventType
{
    CREATE,
    CREATED,

    LOCK,
    LOCKED,

    COMPLETE,
    COMPLETED;
}
