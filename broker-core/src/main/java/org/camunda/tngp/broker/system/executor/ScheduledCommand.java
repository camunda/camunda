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
package org.camunda.tngp.broker.system.executor;

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
