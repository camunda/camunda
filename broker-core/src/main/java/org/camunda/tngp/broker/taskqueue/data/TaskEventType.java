package org.camunda.tngp.broker.taskqueue.data;

public enum TaskEventType
{
    CREATE(0),
    CREATED(1),

    LOCK(2),
    LOCKED(3),
    LOCK_REJECTED(4),

    COMPLETE(5),
    COMPLETED(6),
    COMPLETE_REJECTED(7),

    EXPIRE_LOCK(8),
    LOCK_EXPIRED(9),
    LOCK_EXPIRATION_REJECTED(10),

    FAIL(11),
    FAILED(12),
    FAIL_REJECTED(13),

    UPDATE_RETRIES(14),
    RETRIES_UPDATED(15),
    UPDATE_RETRIES_REJECTED(16);

    // don't change the ids because the task stream processor use them for the index
    private final int id;

    TaskEventType(int id)
    {
        this.id = id;
    }

    public int id()
    {
        return id;
    }
}
