package org.camunda.tngp.broker.wf.repository.log;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.io.DirectBufferInputStream;

public class WfTypeReader implements BufferReader
{
    public static final int MAX_LENGTH = 1024 * 1024 * 2;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final WfTypeDecoder decoder = new WfTypeDecoder();

    protected final UnsafeBuffer typeKeyBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();

        final int wfTypeKeyLength = decoder.typeKeyLength();

        offset += TaskInstanceDecoder.taskTypeHeaderLength();

        typeKeyBuffer.wrap(buffer, offset, wfTypeKeyLength);

        offset += wfTypeKeyLength;

        decoder.limit(offset);

        final int resourceLength = decoder.resourceLength();

        offset += TaskInstanceDecoder.payloadHeaderLength();

        resourceBuffer.wrap(buffer, offset, resourceLength);
    }

    public int resourceId()
    {
        return headerDecoder.resourceId();
    }

    public int shardId()
    {
        return headerDecoder.shardId();
    }

    public DirectBuffer getTypeKey()
    {
        return typeKeyBuffer;
    }

    public DirectBuffer getResource()
    {
        return resourceBuffer;
    }

    public long id()
    {
        return decoder.id();
    }

    public int version()
    {
        return decoder.version();
    }

    public long prevVersionPosition()
    {
        return decoder.prevVersionPosition();
    }

    public BpmnModelInstance asModelInstance()
    {
        return Bpmn.readModelFromStream(new DirectBufferInputStream(resourceBuffer));
    }

}
