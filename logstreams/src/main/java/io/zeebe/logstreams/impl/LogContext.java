package io.zeebe.logstreams.impl;

import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.StateMachine;

import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;

/**
 * Represents the log context which is used by the log stream controller's
 * to transmit the current context to their different states.
 */
public class LogContext extends SimpleStateMachineContext
{
    private long currentBlockAddress = INVALID_ADDRESS;
    private long firstEventPosition = 0;

    LogContext(StateMachine<?> stateMachine)
    {
        super(stateMachine);
    }

    public long getFirstEventPosition()
    {
        return firstEventPosition;
    }

    public void setFirstEventPosition(long lastPosition)
    {
        this.firstEventPosition = lastPosition;
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
        setFirstEventPosition(0);
    }

    public void reset()
    {
        this.firstEventPosition = 0;
        resetCurrentBlockAddress();
    }
}
