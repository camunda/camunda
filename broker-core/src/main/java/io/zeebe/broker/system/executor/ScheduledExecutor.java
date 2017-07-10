/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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