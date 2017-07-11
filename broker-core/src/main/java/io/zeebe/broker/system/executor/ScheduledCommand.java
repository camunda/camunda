/*
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

/**
 * A task which is scheduled to run after a given delay, or to execute
 * periodically.
 */
public interface ScheduledCommand
{
    /**
     * Returns the due date of the next execution in millis.
     */
    long getDueDate();

    /**
     * Returns the given period between executions in millis, if the task is
     * executed periodically. Otherwise, return a negative value.
     */
    long getPeriod();

    /**
     * Mark the task as cancelled so that it will be not executed anymore.
     */
    void cancel();

    /**
     * Return <code>true</code>, if the task is marked as cancelled.
     */
    boolean isCancelled();
}
