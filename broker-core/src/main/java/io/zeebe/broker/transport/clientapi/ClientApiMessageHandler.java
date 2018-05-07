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
package io.zeebe.broker.transport.clientapi;

import java.util.EnumMap;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.event.processor.TopicSubscriberEvent;
import io.zeebe.broker.event.processor.TopicSubscriptionEvent;
import io.zeebe.broker.task.data.TaskRecord;
import io.zeebe.broker.transport.controlmessage.ControlMessageRequestHeaderDescriptor;
import io.zeebe.broker.workflow.data.DeploymentRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;

public class ClientApiMessageHandler implements ServerMessageHandler, ServerRequestHandler
{
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder = new ExecuteCommandRequestDecoder();
    protected final ControlMessageRequestHeaderDescriptor controlMessageRequestHeaderDescriptor = new ControlMessageRequestHeaderDescriptor();

    protected final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    protected final Int2ObjectHashMap<Partition> leaderPartitions = new Int2ObjectHashMap<>();
    protected final RecordMetadata eventMetadata = new RecordMetadata();
    protected final LogStreamWriter logStreamWriter = new LogStreamWriterImpl();

    protected final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();
    protected final Dispatcher controlMessageDispatcher;
    protected final ClaimedFragment claimedControlMessageFragment = new ClaimedFragment();

    protected final EnumMap<ValueType, UnpackedObject> recordsByType = new EnumMap<>(ValueType.class);

    public ClientApiMessageHandler(final Dispatcher controlMessageDispatcher)
    {
        this.controlMessageDispatcher = controlMessageDispatcher;

        initEventTypeMap();
    }

    private void initEventTypeMap()
    {
        recordsByType.put(ValueType.DEPLOYMENT, new DeploymentRecord());
        recordsByType.put(ValueType.TASK, new TaskRecord());
        recordsByType.put(ValueType.WORKFLOW_INSTANCE, new WorkflowInstanceRecord());
        recordsByType.put(ValueType.SUBSCRIBER, new TopicSubscriberEvent());
        recordsByType.put(ValueType.SUBSCRIPTION, new TopicSubscriptionEvent());
        recordsByType.put(ValueType.TOPIC, new TopicRecord());
    }

    private boolean handleExecuteCommandRequest(
            final ServerOutput output,
            final RemoteAddress requestAddress,
            final long requestId,
            final RecordMetadata eventMetadata,
            final DirectBuffer buffer,
            final int messageOffset,
            final int messageLength)
    {
        executeCommandRequestDecoder.wrap(buffer, messageOffset + messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

        final int partitionId = executeCommandRequestDecoder.partitionId();
        final long key = executeCommandRequestDecoder.key();

        final Partition partition = leaderPartitions.get(partitionId);

        if (partition == null)
        {
            return errorResponseWriter
                .errorCode(ErrorCode.PARTITION_NOT_FOUND)
                .errorMessage("Cannot execute command. Partition with id '%d' not found", partitionId)
                .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
        }

        final ValueType eventType = executeCommandRequestDecoder.valueType();
        final short intent = executeCommandRequestDecoder.intent();
        final UnpackedObject event = recordsByType.get(eventType);

        if (event == null)
        {
            return errorResponseWriter
                    .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                    .errorMessage("Cannot execute command. Invalid event type '%s'.", eventType.name())
                    .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
        }

        final int eventOffset = executeCommandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
        final int eventLength = executeCommandRequestDecoder.valueLength();

        event.reset();

        try
        {
            // verify that the event / command is valid
            event.wrap(buffer, eventOffset, eventLength);
        }
        catch (Throwable t)
        {
            return errorResponseWriter
                    .errorCode(ErrorCode.INVALID_MESSAGE)
                    .errorMessage("Cannot deserialize command: '%s'.", concatErrorMessages(t))
                    .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
        }

        eventMetadata.recordType(RecordType.COMMAND);
        eventMetadata.intent(intent);
        eventMetadata.valueType(eventType);

        logStreamWriter.wrap(partition.getLogStream());

        if (key != ExecuteCommandRequestDecoder.keyNullValue())
        {
            logStreamWriter.key(key);
        }
        else
        {
            logStreamWriter.positionAsKey();
        }

        final long eventPosition = logStreamWriter
                .metadataWriter(eventMetadata)
                .value(buffer, eventOffset, eventLength)
                .tryWrite();

        return eventPosition >= 0;
    }

    private String concatErrorMessages(Throwable t)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(t.getMessage());

        while (t.getCause() != null)
        {
            t = t.getCause();

            sb.append("; ");
            sb.append(t.getMessage());
        }

        return sb.toString();
    }

    private boolean handleControlMessageRequest(
            final RecordMetadata eventMetadata,
            final DirectBuffer buffer,
            final int messageOffset,
            final int messageLength)
    {
        boolean isHandled = false;
        long publishPosition;

        do
        {
            publishPosition = controlMessageDispatcher.claim(claimedControlMessageFragment, ControlMessageRequestHeaderDescriptor.framedLength(messageLength));
        }
        while (publishPosition == -2);

        if (publishPosition >= 0)
        {
            final MutableDirectBuffer writeBuffer = claimedControlMessageFragment.getBuffer();
            int writeBufferOffset = claimedControlMessageFragment.getOffset();

            controlMessageRequestHeaderDescriptor
                .wrap(writeBuffer, writeBufferOffset)
                .streamId(eventMetadata.getRequestStreamId())
                .requestId(eventMetadata.getRequestId());

            writeBufferOffset += ControlMessageRequestHeaderDescriptor.headerLength();

            writeBuffer.putBytes(writeBufferOffset, buffer, messageOffset, messageLength);

            claimedControlMessageFragment.commit();

            isHandled = true;
        }

        return isHandled;
    }

    public void addPartition(final Partition partition)
    {
        cmdQueue.add(() -> leaderPartitions.put(partition.getInfo().getPartitionId(), partition));
    }

    public void removePartition(final Partition partition)
    {
        cmdQueue.add(() -> leaderPartitions.remove(partition.getInfo().getPartitionId()));
    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        drainCommandQueue();

        messageHeaderDecoder.wrap(buffer, offset);

        final int templateId = messageHeaderDecoder.templateId();
        final int clientVersion = messageHeaderDecoder.version();


        if (clientVersion > Protocol.PROTOCOL_VERSION)
        {
            return errorResponseWriter
                .errorCode(ErrorCode.INVALID_CLIENT_VERSION)
                .errorMessage("Client has newer version than broker (%d > %d)", clientVersion, Protocol.PROTOCOL_VERSION)
                .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
        }

        eventMetadata.reset();
        eventMetadata.protocolVersion(clientVersion);
        eventMetadata.requestId(requestId);
        eventMetadata.requestStreamId(remoteAddress.getStreamId());

        final boolean isHandled;
        switch (templateId)
        {
            case ExecuteCommandRequestDecoder.TEMPLATE_ID:

                isHandled = handleExecuteCommandRequest(
                        output,
                        remoteAddress,
                        requestId,
                        eventMetadata,
                        buffer,
                        offset,
                        length);
                break;

            case ControlMessageRequestDecoder.TEMPLATE_ID:
                isHandled = handleControlMessageRequest(eventMetadata, buffer, offset, length);
                break;

            default:
                isHandled = errorResponseWriter
                        .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                        .errorMessage("Cannot handle message. Template id '%d' is not supported.", templateId)
                        .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
                break;
        }

        return isHandled;
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        // ignore; currently no incoming single-message client interactions
        return true;
    }

    private void drainCommandQueue()
    {
        while (!cmdQueue.isEmpty())
        {
            final Runnable runnable = cmdQueue.poll();
            if (runnable != null)
            {
                cmdConsumer.accept(runnable);
            }
        }
    }

}
