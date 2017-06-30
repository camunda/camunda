package io.zeebe.util.actor;

/**
 * A reference to a scheduled actor.
 */
public interface ActorReference
{
    /**
     * Remove the actor from the scheduler, so it stops to invoke them.
     */
    void close();

    /**
     * Return the name of the actor.
     */
    String name();
}
