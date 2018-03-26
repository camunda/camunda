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
package io.zeebe.broker.topic;

import java.util.function.Predicate;

import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.logstreams.log.LoggedEvent;

public interface StreamProcessorControl
{

    void unblock();

    void blockAfterEvent(Predicate<LoggedEvent> test);

    void blockAfterTaskEvent(Predicate<TypedEvent<TaskEvent>> test);

    void blockAfterDeploymentEvent(Predicate<TypedEvent<DeploymentEvent>> test);

    void blockAfterIncidentEvent(Predicate<TypedEvent<IncidentEvent>> test);

    void blockAfterTopicEvent(Predicate<TypedEvent<TopicEvent>> test);

    void blockAfterPartitionEvent(Predicate<TypedEvent<PartitionEvent>> test);

    void purgeSnapshot();

    /**
     * @return true if the event to block on has been processed and the stream processor won't handle
     *   any more events until {@link #unblock()} is called.
     */
    boolean isBlocked();

    void close();

    void start();

    void restart();
}
