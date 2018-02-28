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
package io.zeebe.util.sched;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import io.zeebe.util.sched.clock.ActorClock;

/**
 * Scheduler for tasks which are I/O bound (predominantly blocking on I/O).
 *<p>
 * The scheduler maintains a max concurrency by I/O device, allowing to limit
 * how many tasks submitting I/O operations to the same device can run in parallel.
 *<p>
 * This scheduler is threadsafe and there should be one instance per {@link ActorThreadGroup}.
 */
public class IoScheduler implements TaskScheduler
{
    private final ReentrantLock lock = new ReentrantLock();

    private final IntFunction<ActorTask> getTaskFn;

    private final int numOfDevices;
    private final int[] deviceMaxConcurrency;
    private final int[] pendingOperatsByDevice;

    private int currentDevice = 0;

    /**
     * @param getTaskFn the function which allows to retrieve the next task by I/O device.
     * @param deviceMaxConcurrency for each device, the max level of concurrency to allow for.
     */
    public IoScheduler(IntFunction<ActorTask> getTaskFn, int[] deviceMaxConcurrency)
    {
        this.getTaskFn = getTaskFn;
        this.numOfDevices = deviceMaxConcurrency.length;
        this.deviceMaxConcurrency = deviceMaxConcurrency;
        this.pendingOperatsByDevice = new int[numOfDevices];
    }

    @Override
    public ActorTask getNextTask(ActorClock now)
    {
        lock.lock();
        try
        {
            return doGetNextTask();
        }
        finally
        {
            lock.unlock();
        }
    }

    private ActorTask doGetNextTask()
    {
        ActorTask nextTask = null;

        for (int i = currentDevice; i < currentDevice + numOfDevices; i++)
        {
            final int device = i % numOfDevices;

            if (pendingOperatsByDevice[device] < deviceMaxConcurrency[device])
            {
                nextTask = getTaskFn.apply(device);

                if (nextTask != null)
                {
                    currentDevice = device;
                    ++pendingOperatsByDevice[device];
                    break;
                }
            }
        }

        return nextTask;
    }

    @Override
    public void onTaskReleased(ActorTask task)
    {
        lock.lock();
        try
        {
            --pendingOperatsByDevice[task.getDeviceId()];
        }
        finally
        {
            lock.unlock();
        }
    }
}
