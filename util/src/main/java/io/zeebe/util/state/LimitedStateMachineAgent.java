/*
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
package io.zeebe.util.state;

/**
 * State machine agent which only drains a limited number of commands per invocation.
 * Default limit is {@link LimitedStateMachineAgent#DEFAULT_DRAIN_LIMIT}.
 */
public class LimitedStateMachineAgent<C extends StateMachineContext> extends StateMachineAgent<C>
{

    public static final int DEFAULT_DRAIN_LIMIT = 1;

    protected final int drainLimit;

    public LimitedStateMachineAgent(final StateMachine<C> stateMachine)
    {
        this(stateMachine, DEFAULT_DRAIN_LIMIT);
    }

    public LimitedStateMachineAgent(final StateMachine<C> stateMachine, final int drainLimit)
    {
        super(stateMachine);

        this.drainLimit = drainLimit;
    }

    @Override
    protected int drainCommandQueue()
    {
        return commandQueue.drain(commandConsumer, drainLimit);
    }

}
