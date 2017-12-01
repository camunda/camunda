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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.IntObjectBiConsumer;

public class PartitionResponder implements IntObjectBiConsumer<DirectBuffer>, StreamProcessorLifecycleAware
{
    protected final PartitionsResponse responseContent = new PartitionsResponse();
    protected final ControlMessageResponseWriter responseWriter;

    protected AtomicReference<TypedStreamProcessor> streamProcessorRef = new AtomicReference<>(null);

    public PartitionResponder(ServerOutput output)
    {
        this.responseWriter = new ControlMessageResponseWriter(output);
    }

    public CompletableFuture<Void> sendPartitions(int requestStreamId, long requestId)
    {
        final TypedStreamProcessor streamProcessor = streamProcessorRef.get();
        if (streamProcessor != null)
        {
            return streamProcessor.runAsync(f ->
            {
                // TODO: handle return value in case of backpressure
                // a command handler should be able to postpone a command in this case
                // => https://github.com/zeebe-io/zeebe/issues/555
                responseWriter
                    .dataWriter(responseContent)
                    .tryWriteResponse(requestStreamId, requestId);

                f.complete(null);
            });
        }
        else
        {
            return null;
        }
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.streamProcessorRef.set(streamProcessor);
    }

    @Override
    public void onClose()
    {
        this.streamProcessorRef.set(null);
    }

    @Override
    public void accept(int partitionId, DirectBuffer topicName)
    {
        responseContent.addPartition(partitionId, topicName);
    }

    public void registerWith(TypedEventStreamProcessorBuilder builder)
    {
        builder.withStateResource(responseContent)
            .withListener(this);
    }
}
