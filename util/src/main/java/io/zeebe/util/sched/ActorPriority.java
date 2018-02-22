package io.zeebe.util.sched;

/**
 * Default Actor Priority Classes
 */
public enum ActorPriority
{
    HIGH(0),

    REGULAR(1),

    LOW(2);

    private int priorityClass;

    ActorPriority(int priorityClass)
    {
        this.priorityClass = priorityClass;
    }

    public int getPriorityClass()
    {
        return priorityClass;
    }
}
