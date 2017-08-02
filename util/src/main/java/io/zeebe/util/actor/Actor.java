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
package io.zeebe.util.actor;

/**
 * An actor is an abstraction of a function which is scheduled to do available
 * work. The scheduler invokes the actor constantly beside other actors within a
 * duty cycle. The actors may run concurrently and are not binded to a fixed
 * thread.
 *
 * <p>
 * An actor should:
 * <li>be non-blocking
 * <li>do only a limited amount of work in one cycle
 * <li>communicate asynchronous with other actors
 */
public interface Actor
{
    int PRIORITY_OFF = 0;
    int PRIORITY_LOW = 1;
    int PRIORITY_MIDDLE = 50;
    int PRIORITY_HIGH = 100;

    /**
     * An actor should implement this method to do the available work.
     * <p>
     * The return value is used for implementing an idle strategy that can be
     * employed when no work is currently available.
     *
     * @return 0 to indicate no work was currently available, a positive value
     *         otherwise.
     * @throws Exception
     *             if an error has occurred
     */
    int doWork() throws Exception;

    /**
     * Returns the priority of the actor. Must be between 1 (low) and 100
     * (high). An implementation can use a static value or resolve the priority
     * dynamically, e.g., depends on the current time.
     *
     * <p>
     * The priority influences how often the actor is invoked by the scheduler.
     *
     * <p>
     * Default priority is {@value #PRIORITY_LOW}.
     *
     * @param now
     *            the current time in nano seconds
     */
    default int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    /**
     * Returns the name of the actor.
     *
     * <p>
     * Default is the class name.
     */
    default String name()
    {
        return getClass().getSimpleName();
    }
}
