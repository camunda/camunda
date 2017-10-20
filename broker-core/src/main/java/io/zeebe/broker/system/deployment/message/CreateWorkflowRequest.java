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
package io.zeebe.broker.system.deployment.message;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CreateWorkflowRequest implements BufferReader, BufferWriter
{

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final CreateWorkflowRequestEncoder bodyEncoder = new CreateWorkflowRequestEncoder();

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final CreateWorkflowRequestDecoder bodyDecoder = new CreateWorkflowRequestDecoder();

    private int partitionId = CreateWorkflowRequestEncoder.partitionIdNullValue();
    private long workflowKey = CreateWorkflowRequestEncoder.workflowKeyNullValue();
    private long deploymentKey = CreateWorkflowRequestEncoder.deploymentKeyNullValue();
    private int version = CreateWorkflowRequestEncoder.versionNullValue();

    private final DirectBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
    private final DirectBuffer bpmnXml = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                CreateWorkflowRequestEncoder.bpmnProcessIdHeaderLength() +
                bpmnProcessId.capacity() +
                CreateWorkflowRequestEncoder.bpmnXmlHeaderLength() +
                bpmnXml.capacity();
    }

    public CreateWorkflowRequest partitionId(int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public CreateWorkflowRequest workflowKey(long workflowKey)
    {
        this.workflowKey = workflowKey;
        return this;
    }

    public CreateWorkflowRequest deploymentKey(long deploymentKey)
    {
        this.deploymentKey = deploymentKey;
        return this;
    }

    public CreateWorkflowRequest version(int version)
    {
        this.version = version;
        return this;
    }

    public CreateWorkflowRequest bpmnProcessId(DirectBuffer bpmnProcessId)
    {
        this.bpmnProcessId.wrap(bpmnProcessId);
        return this;
    }

    public CreateWorkflowRequest bpmnXml(DirectBuffer bpmnXml)
    {
        this.bpmnXml.wrap(bpmnXml);
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
            .partitionId(partitionId)
            .workflowKey(workflowKey)
            .deploymentKey(deploymentKey)
            .version(version)
            .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity())
            .putBpmnXml(bpmnXml, 0, bpmnXml.capacity());
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

        partitionId = bodyDecoder.partitionId();
        workflowKey = bodyDecoder.workflowKey();
        deploymentKey = bodyDecoder.deploymentKey();
        version = bodyDecoder.version();

        offset += headerDecoder.blockLength();

        final int bpmnProcessIdLength = bodyDecoder.bpmnProcessIdLength();
        offset += CreateWorkflowRequestEncoder.bpmnProcessIdHeaderLength();

        bpmnProcessId.wrap(buffer, offset, bpmnProcessIdLength);

        offset += bpmnProcessIdLength;
        bodyDecoder.limit(offset);

        final int bpmnXmlLength = bodyDecoder.bpmnXmlLength();
        offset += CreateWorkflowRequestEncoder.bpmnXmlHeaderLength();

        bpmnXml.wrap(buffer, offset, bpmnXmlLength);
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public long getWorkflowKey()
    {
        return workflowKey;
    }

    public long getDeploymentKey()
    {
        return deploymentKey;
    }

    public int getVersion()
    {
        return version;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public DirectBuffer getBpmnXml()
    {
        return bpmnXml;
    }

}
