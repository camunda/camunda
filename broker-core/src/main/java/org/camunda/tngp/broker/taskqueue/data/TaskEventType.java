package org.camunda.tngp.broker.taskqueue.data;

public enum TaskEventType
{
    CREATE,
    CREATED,

    LOCK,
    LOCKED,
    LOCK_FAILED,

    COMPLETE,
    COMPLETED,
    COMPLETE_FAILED,

    EXPIRE_LOCK,
    LOCK_EXPIRED,
    LOCK_EXPIRATION_FAILED,

    ABORT,
    ABORTED,
    ABORT_FAILED;

}
