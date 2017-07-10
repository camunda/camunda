/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
