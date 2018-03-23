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
package io.zeebe.broker.task.processor;

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;

public class TaskEventWriter implements AutoCloseable
{

    private final TypedStreamWriter writer;
    private final TypedStreamReader reader;

    public TaskEventWriter(
            TypedStreamWriter writer,
            TypedStreamReader reader)
    {
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public void close()
    {
        this.reader.close();
    }

    /**
     * Writes a follow-up event copying all properties of the source event and updating the state.
     */
    public boolean tryWriteTaskEvent(final long sourceEventPosition, TaskState newState)
    {
        final TypedEvent<TaskEvent> event = reader.readValue(sourceEventPosition, TaskEvent.class);

        final TaskEvent taskEvent = event.getValue().setState(newState);

        return writer.writeFollowupEvent(event.getKey(), taskEvent) >= 0;
    }
}
