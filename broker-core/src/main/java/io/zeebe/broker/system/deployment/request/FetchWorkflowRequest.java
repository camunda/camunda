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
package io.zeebe.broker.system.deployment.request;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class FetchWorkflowRequest implements BufferReader, BufferWriter
{
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final FetchWorkflowRequestEncoder bodyEncoder = new FetchWorkflowRequestEncoder();

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final FetchWorkflowRequestDecoder bodyDecoder = new FetchWorkflowRequestDecoder();

    private long workflowKey = FetchWorkflowRequestEncoder.workflowKeyNullValue();
    private int version = FetchWorkflowRequestEncoder.versionNullValue();
    private final DirectBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
    private final DirectBuffer topicName = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            FetchWorkflowRequestEncoder.bpmnProcessIdHeaderLength() +
            bpmnProcessId.capacity() +
            FetchWorkflowRequestEncoder.topicNameHeaderLength() +
            topicName.capacity();
    }

    public FetchWorkflowRequest workflowKey(long workflowKey)
    {
        this.workflowKey = workflowKey;
        return this;
    }

    public FetchWorkflowRequest version(int version)
    {
        this.version = version;
        return this;
    }

    public FetchWorkflowRequest latestVersion()
    {
        return version(FetchWorkflowRequestEncoder.versionMaxValue());
    }

    public FetchWorkflowRequest bpmnProcessId(DirectBuffer bpmnProcessId)
    {
        this.bpmnProcessId.wrap(bpmnProcessId);
        return this;
    }

    public FetchWorkflowRequest topicName(DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .workflowKey(workflowKey)
            .version(version)
            .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity())
            .putTopicName(topicName, 0, topicName.capacity());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer,
            offset,
            headerDecoder.blockLength(),
            headerDecoder.version());

        workflowKey = bodyDecoder.workflowKey();
        version = bodyDecoder.version();

        offset += headerDecoder.blockLength();

        // bpmn process id

        final int bpmnProcessIdLength = bodyDecoder.bpmnProcessIdLength();
        offset += FetchWorkflowRequestDecoder.bpmnProcessIdHeaderLength();

        bpmnProcessId.wrap(buffer, offset, bpmnProcessIdLength);

        offset += bpmnProcessIdLength;
        bodyDecoder.limit(offset);

        // bpmn process id

        final int topicNameLength = bodyDecoder.topicNameLength();
        offset += FetchWorkflowRequestDecoder.topicNameHeaderLength();

        topicName.wrap(buffer, offset, topicNameLength);

        offset += topicNameLength;
        bodyDecoder.limit(offset);
    }

    public long getWorkflowKey()
    {
        return workflowKey;
    }

    public int getVersion()
    {
        return version;
    }

    public boolean isLatestVersion()
    {
        return version == FetchWorkflowRequestDecoder.versionMaxValue();
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    public FetchWorkflowRequest reset()
    {
        workflowKey = FetchWorkflowRequestEncoder.workflowKeyNullValue();
        version = FetchWorkflowRequestEncoder.versionNullValue();
        bpmnProcessId.wrap(0, 0);
        topicName.wrap(0, 0);

        return this;
    }
}
