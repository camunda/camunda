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
package io.zeebe.broker.util;

import java.util.stream.Stream;

import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.topic.Records;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.test.util.stream.StreamWrapper;

public class RecordStream extends StreamWrapper<LoggedEvent>
{

    public RecordStream(Stream<LoggedEvent> stream)
    {
        super(stream);
    }

    public TypedRecordStream<TaskEvent> onlyTaskRecords()
    {
        return new TypedRecordStream<>(
            filter(Records::isTaskRecord)
            .map(e -> CopiedTypedEvent.toTypedEvent(e, TaskEvent.class)));
    }

    public TypedRecordStream<IncidentEvent> onlyIncidentRecords()
    {
        return new TypedRecordStream<>(
            filter(Records::isIncidentRecord)
            .map(e -> CopiedTypedEvent.toTypedEvent(e, IncidentEvent.class)));
    }


    public TypedRecordStream<DeploymentEvent> onlyDeploymentRecords()
    {
        return new TypedRecordStream<>(
            filter(Records::isDeploymentRecord)
            .map(e -> CopiedTypedEvent.toTypedEvent(e, DeploymentEvent.class)));
    }

    public TypedRecordStream<TopicEvent> onlyTopicRecords()
    {
        return new TypedRecordStream<>(
            filter(Records::isTopicRecord)
            .map(e -> CopiedTypedEvent.toTypedEvent(e, TopicEvent.class)));
    }


}
