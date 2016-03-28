package org.camunda.tngp.taskqueue;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetSocketAddress;

import static org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceEncoder.*;

import org.camunda.tngp.taskqueue.protocol.AckDecoder;
import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceEncoder;
import org.camunda.tngp.taskqueue.protocol.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.protocol.MessageHeaderEncoder;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportRequest;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class TaskClient
{

    public static void main(String[] args)
    {
        final CreateTaskInstanceEncoder createTaskInstanceEncoder = new CreateTaskInstanceEncoder();
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final AckDecoder ackDecoder = new AckDecoder();
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

        final Transport clientTransport = Transports.createTransport("client")
                .build();

        final TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);

        final ClientChannel channel = clientTransport.createClientChannel(new InetSocketAddress("localhost", 8080))
                .requestResponseChannel(connectionPool)
                .connect();

        final byte[] taskType = "delete-order".getBytes(UTF_8);
        final byte[] payload = "{}".getBytes(UTF_8);

        try (final TransportConnection connection = connectionPool.openConnection())
        {
            try (final TransportRequest request = connection.openRequest(channel.getId(),
                    headerEncoder.encodedLength() +
                    taskTypeHeaderLength() + taskType.length +
                    payloadHeaderLength() + payload.length))
            {
                if (request != null)
                {
                    final MutableDirectBuffer buffer = request.getClaimedRequestBuffer();
                    int offset = request.getClaimedOffset();

                    headerEncoder.wrap(buffer, offset);
                    headerEncoder
                            .blockLength(createTaskInstanceEncoder.sbeBlockLength())
                            .schemaId(createTaskInstanceEncoder.sbeSchemaId())
                            .templateId(createTaskInstanceEncoder.sbeTemplateId())
                            .version(createTaskInstanceEncoder.sbeSchemaVersion());

                    offset += headerEncoder.encodedLength();

                    createTaskInstanceEncoder.wrap(buffer, offset);
                    createTaskInstanceEncoder
                            .putTaskType(taskType, 0, taskType.length)
                            .putPayload(payload, 0, payload.length);

                    request.commit();

                    request.awaitResponse();

                    int readOffset = 0;

                    headerDecoder.wrap(request.getResponseBuffer(), readOffset);

                    readOffset += headerDecoder.encodedLength();

                    if(headerDecoder.templateId() == AckDecoder.TEMPLATE_ID)
                    {
                        ackDecoder.wrap(request.getResponseBuffer(), readOffset, headerDecoder.blockLength(), headerDecoder.version());

                        System.out.println("Ack for task "+ ackDecoder.taskId());
                    }
                }
            }
        }
        finally
        {
            connectionPool.close();
            clientTransport.close();
        }

    }

}
