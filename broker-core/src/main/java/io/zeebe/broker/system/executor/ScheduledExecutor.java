/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.system.executor;

import java.time.Duration;

/**
 * An executor that can schedule commands to run after a given delay, or to
 * execute periodically.
 */
public interface ScheduledExecutor
{

    /**
     * Execute the command after the given delay.
     *
     * @param command
     *            the task to execute
     * @param delay
     *            the time from now to delay execution
     * @return the scheduled task
     */
    ScheduledCommand schedule(Runnable command, Duration delay);

    /**
     * Execute the given command periodically. The execution can be stopped
     * using {@linkplain ScheduledCommand#cancel()}. If the command throws an
     * exception then the execution is cancelled and will not be repeated.
     *
     * @param command
     *            the task to execute periodically
     * @param period
     *            the period between executions
     * @return the scheduled task
     */
    ScheduledCommand scheduleAtFixedRate(Runnable command, Duration period);

    /**
     * Execute the command periodically after the given delay. The execution can
     * be stopped using {@linkplain ScheduledCommand#cancel()}. If the command
     * throws an exception then the execution is cancelled and will not be
     * repeated.
     *
     * @param command
     *            the task to execute periodically
     * @param initialDelay
     *            the time from now to delay first execution
     * @param period
     *            the period between executions
     * @return the scheduled task
     */
    ScheduledCommand scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);

}