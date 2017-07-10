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
package io.zeebe.util.actor;

import java.util.function.Function;
import java.util.function.Supplier;

import io.zeebe.util.Loggers;
import org.slf4j.Logger;

public class SingleThreadActorScheduler implements ActorScheduler
{
    public static final Logger LOG = Loggers.ACTOR_LOGGER;

    private final ActorRunner runner;
    private final Thread runnerThread;

    private final Function<Actor, ActorReferenceImpl> actorRefFactory;

    public SingleThreadActorScheduler(Supplier<ActorRunner> runnerFactory, Function<Actor, ActorReferenceImpl> actorRefFactory)
    {
        this.actorRefFactory = actorRefFactory;

        this.runner = runnerFactory.get();
        this.runnerThread = new Thread(runner, "actor-runner");

        this.runnerThread.start();
    }

    @Override
    public ActorReference schedule(Actor actor)
    {
        final ActorReferenceImpl actorRef = actorRefFactory.apply(actor);

        runner.submitActor(actorRef);

        return actorRef;
    }

    @Override
    public void close()
    {
        runner.close();

        try
        {
            runnerThread.join(5000);
        }
        catch (Exception e)
        {
            LOG.error("Actor Runner did not exit within 5 second");
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ActorScheduler [runner=");
        builder.append(runner);
        builder.append("]");
        return builder.toString();
    }

}
