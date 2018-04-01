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
package io.zeebe.broker.system.log;

import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.log.PendingPartitionsIndex.PendingPartition;

public class CompletePartitionProcessor implements TypedEventProcessor<PartitionEvent>
{

    protected final PendingPartitionsIndex partitions;

    public CompletePartitionProcessor(PendingPartitionsIndex partitions)
    {
        this.partitions = partitions;
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();
        final PendingPartition partition = partitions.get(value.getPartitionId());

        if (partition != null)
        {
            value.setState(PartitionState.CREATED);
        }
        else
        {
            value.setState(PartitionState.CREATE_COMPLETE_REJECTED);
        }
    }

    @Override
    public boolean executeSideEffects(TypedEvent<PartitionEvent> event, TypedResponseWriter responseWriter)
    {
        return true;
    }

    @Override
    public long writeEvent(TypedEvent<PartitionEvent> event, TypedStreamWriter writer)
    {
        return writer.writeFollowupEvent(event.getKey(), event.getValue());
    }

    @Override
    public void updateState(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        if (value.getState() == PartitionState.CREATED)
        {
            partitions.removePartitionKey(value.getPartitionId());
        }
    }

}
