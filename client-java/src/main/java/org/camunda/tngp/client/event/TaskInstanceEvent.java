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
package org.camunda.tngp.client.event;

import java.time.Instant;

/**
 * Represents one task instance {@link Event}.
 */
public interface TaskInstanceEvent extends Event
{
    int STATE_NEW = 0;
    int STATE_LOCKED = 1;
    int STATE_COMPLETED = 2;

    /**
     * @return the task's id
     */
    long getId();

    /**
     * @return the task's type
     */
    String getType();

    /**
     * @return the id of the workflow instance this task belongs to. May be
     *         <code>null</code> if this is a standalone task.
     */
    Long getWorkflowInstanceId();

    /**
     * @return the time until when the task is locked. May be <code>null</code>
     *         if the task is not looked.
     */
    Instant getLockExpirationTime();

    /**
     * @return the id of the owner which locked the task. May be
     *         <code>null</code> if the task is not looked.
     */
    Long getLockOwnerId();

    /**
     * @return <code>true</code> if the task is not locked or completed
     */
    boolean isNew();

    /**
     * @return <code>true</code> if the task is locked and not completed yet
     */
    boolean isLocked();

    /**
     * @return <code>true</code> if the task is completed
     */
    boolean isCompleted();

    /**
     * @return the task's state
     */
    int getState();

}