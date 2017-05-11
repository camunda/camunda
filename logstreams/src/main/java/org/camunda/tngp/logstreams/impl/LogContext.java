package org.camunda.tngp.logstreams.impl;

import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;

import static org.camunda.tngp.logstreams.log.LogStreamUtil.INVALID_ADDRESS;

/**
 * Represents the log context which is used by the log stream controller's
 * to transmit the current context to their different states.
 */
public class LogContext extends SimpleStateMachineContext
{
    private long currentBlockAddress = INVALID_ADDRESS;
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

    public boolean hasCurrentBlockAddress()
    {
        return currentBlockAddress != INVALID_ADDRESS;
    }

    public void resetCurrentBlockAddress()
    {
        setCurrentBlockAddress(INVALID_ADDRESS);
    }

    public void resetLastPosition()
    {
        setLastPosition(0);
    }

    public void reset()
    {
        this.lastPosition = 0;
        resetCurrentBlockAddress();
    }
}
