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

/**
 * Describes the state in which an actor task currently is. The state is used internally by the scheduler
 * to manage tasks.
 *
 */
public enum ActorState
{
    /**
     * The task is not scheduled
     */
    NOT_SCHEDULED,

    /**
     * The task is running on a thread
     */
    ACTIVE,

    /**
     * The task is currently queued to be run on a thread
     */
    QUEUED,

    /**
     * Top-level tasks only:
     * The actor is waiting on more work to become available. An actor is in this state if it has no more tasks to run and has
     * subscribed to more work to become available through
     * <ul>
     * <li> {@link ActorControl#consume(ByteSource, Runnable, java.time.Duration)}</li>
     * <li> {@link ActorControl#poll(java.util.function.BooleanSupplier, Runnable, java.time.Duration)}</li>
     * <li> {@link ActorControl#pollBlocking(Runnable, Runnable)}</li>
     * <li> {@link ActorControl#runAtFixedRate(java.time.Duration, Runnable)}</li>
     * <li> {@link ActorControl#runDelayed(java.time.Duration, Runnable)}</li>
     * </ul>
     */
    WAITING,

    WAKING_UP,

    /**
     * The task has terminated. Termination of top level tasks is reached under one of the following conditions:
     * <ul>
     * <li>The actor has no more tasks to run and it has not subscribed to more work</li>
     * <li> {@link ActorControl#close()} was called and the resulting top-level task has terminated</li>
     * <ul>
     */
    TERMINATED
}
