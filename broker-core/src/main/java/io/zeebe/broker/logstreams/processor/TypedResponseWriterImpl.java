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
package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;

public class TypedResponseWriterImpl implements TypedResponseWriter
{
    protected CommandResponseWriter writer;
    protected int partitionId;

    public TypedResponseWriterImpl(ServerOutput output, int partitionId)
    {
        this.writer = new CommandResponseWriter(output);
        this.partitionId = partitionId;
    }

    @Override
    public boolean write(TypedEvent<?> event)
    {
        final BrokerEventMetadata metadata = event.getMetadata();

        return writer
            .partitionId(partitionId)
            .position(0) // TODO: this depends on the value of written event => https://github.com/zeebe-io/zeebe/issues/374
            .key(event.getKey())
            .eventWriter(event.getValue())
            .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
    }

}
