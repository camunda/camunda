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
package io.zeebe.test.util.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.util.actor.*;
import org.junit.rules.ExternalResource;

public class ManualActorScheduler extends ExternalResource implements ActorScheduler
{
    protected static final int MAX_WORK_COUNT = 1_000_000;

    protected AtomicBoolean isRunning = new AtomicBoolean(true);

    protected final List<ActorReferenceImpl> actorRefs = new ArrayList<>();

    @Override
    protected void after()
    {
        waitUntilDone();

        try
        {
            close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public int waitUntilDone()
    {
        int totalWorkCount = 0;

        if (isRunning.get())
        {
            int workCount;

            do
            {
                workCount = 0;

                final ArrayList<ActorReferenceImpl> actorRefList = new ArrayList<>(actorRefs);
                for (int i = 0; i < actorRefList.size() && isRunning.get(); i++)
                {
                    final ActorReferenceImpl actorRef = actorRefList.get(i);
                    if (actorRef != null && !actorRef.isClosed())
                    {
                        try
                        {
                            workCount += actorRef.getActor().doWork();
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                totalWorkCount += workCount;

                if (totalWorkCount > MAX_WORK_COUNT)
                {
                    throw new RuntimeException("work count limit of agent runner service exceeded");
                }
            }
            while (workCount > 0);
        }

        return totalWorkCount;
    }

    @Override
    public void close()
    {
        isRunning.set(false);
    }

    @Override
    public synchronized ActorReference schedule(Actor actor)
    {
        final ActorReferenceImpl scheduledTask = new ActorReferenceImpl(actor, 32);
        actorRefs.add(scheduledTask);

        return scheduledTask;
    }

}
