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
package io.zeebe.dispatcher.impl;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;

public class DispatcherContext
{
    protected ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> dispatcherCommandQueue = new ManyToOneConcurrentArrayQueue<>(100);

    protected Actor conductor;
    protected ActorReference conductorRef;

    public ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> getDispatcherCommandQueue()
    {
        return dispatcherCommandQueue;
    }

    public Actor getConductor()
    {
        return conductor;
    }

    public void setConductor(Actor conductorAgent)
    {
        this.conductor = conductorAgent;
    }

    public void setConductorReference(ActorReference conductorRef)
    {
        this.conductorRef = conductorRef;
    }

    public ActorReference getConductorReference()
    {
        return conductorRef;
    }
}