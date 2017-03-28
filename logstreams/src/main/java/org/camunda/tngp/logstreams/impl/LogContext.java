package org.camunda.tngp.logstreams.impl;

import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;

/**
 * Represents the log context which is used by the log stream controller's
 * to transmit the current context to their different states.
 */
public class LogContext extends SimpleStateMachineContext
{
    private long currentBlockAddress = 0L;
    private long lastPosition = 0;

    LogContext(StateMachine<?> stateMachine)
    {
        super(stateMachine);
    }

    public long getLastPosition()
    {
        return lastPosition;
    }

    public void setLastPosition(long lastPosition)
    {
        this.lastPosition = lastPosition;
    }

    public long getCurrentBlockAddress()
    {
        return currentBlockAddress;
    }

    public void setCurrentBlockAddress(long currentBlockAddress)
    {
        this.currentBlockAddress = currentBlockAddress;
    }

    public void reset()
    {
        this.lastPosition = 0;
        this.currentBlockAddress = 0;
    }
}
