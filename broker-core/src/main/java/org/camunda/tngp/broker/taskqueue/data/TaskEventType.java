package org.camunda.tngp.broker.taskqueue.data;

public enum TaskEventType
{
    CREATE(0),
    CREATED(1),

    LOCK(2),
    LOCKED(3),
    LOCK_FAILED(4),

    COMPLETE(5),
    COMPLETED(6),
    COMPLETE_FAILED(7),

    EXPIRE_LOCK(8),
    LOCK_EXPIRED(9),
    LOCK_EXPIRATION_FAILED(10),

    ABORT(11),
    ABORTED(12),
    ABORT_FAILED(13);

    // don't change the ids because the implementation based on it
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
