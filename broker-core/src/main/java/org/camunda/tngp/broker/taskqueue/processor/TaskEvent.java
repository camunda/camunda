package org.camunda.tngp.broker.taskqueue.processor;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.logstreams.processor.BrokerEvent;
import org.camunda.tngp.broker.taskqueue.processor.stuff.DataStuff;
import org.camunda.tngp.broker.taskqueue.processor.stuff.ListField;
import org.camunda.tngp.broker.taskqueue.processor.stuff.VarLengthField;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.protocol.clientapi.TaskEventDecoder;
import org.camunda.tngp.protocol.clientapi.TaskEventEncoder;
import org.camunda.tngp.protocol.clientapi.TaskEventEncoder.HeadersEncoder;
import org.camunda.tngp.protocol.clientapi.TaskEventType;

public class TaskEvent implements BrokerEvent
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskEventDecoder decoder = new TaskEventDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final TaskEventEncoder encoder = new TaskEventEncoder();

    private TaskEventType evtType;
    private long lockTime;
    private VarLengthField taskType = new VarLengthField();
    private VarLengthField payload = new VarLengthField();
    private ListField<TaskHeader> headers = new ListField<>(TaskHeader.class);

    public void decode(DirectBuffer buffer, int offset)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        final int templateId = headerDecoder.templateId();

        if (templateId == decoder.sbeTemplateId())
        {
            decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

            this.evtType = decoder.eventType();
            this.lockTime = decoder.lockTime();

            final TaskEventDecoder.HeadersDecoder headersDecoder = decoder.headers();
            final Iterator<TaskEventDecoder.HeadersDecoder> headersIterator = headersDecoder.iterator();
            headers.ensureCapacity(headersDecoder.count());

            int i = 0;
            while (headersIterator.hasNext())
            {
                final TaskEventDecoder.HeadersDecoder headerDecoder = headersIterator.next();
                final TaskHeader taskHeader = headers.list[i];
                final VarLengthField headerNameField = taskHeader.getHeaderName();
                final VarLengthField headerValueField = taskHeader.getHeaderValue();

                headerNameField.get(headerDecoder.headerNameLength(), headerDecoder::getHeaderName);
                headerValueField.get(headerDecoder.headerValueLength(), headerDecoder::getHeaderValue);

                i++;
            }

            taskType.get(decoder.taskTypeLength(), decoder::getTaskType);
            payload.get(decoder.payloadLength(), decoder::getPayload);
        }
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                encoder.sbeBlockLength() +
                headers.getLength() +
                taskType.getLength() +
                payload.getLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset);

        headerEncoder.blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset);

        encoder.eventType(evtType)
            .lockTime(lockTime);

        final int headersSize = headers.getSize();
        final HeadersEncoder headersEncoder = encoder.headersCount(headersSize);

        for (int i = 0; i < headersSize; i++)
        {
            final TaskHeader taskHeader = headers.list[i];
            headersEncoder.next();

            taskHeader.headerName.put(headersEncoder::putHeaderName);
            taskHeader.headerValue.put(headersEncoder::putHeaderValue);
        }

        taskType.put(encoder::putTaskType);
        payload.put(encoder::putPayload);
    }

    @Override
    public void reset()
    {
        evtType = TaskEventType.NULL_VAL;
        lockTime = TaskEventEncoder.lockTimeNullValue();
        headers.reset();
        taskType.reset();
        payload.reset();
    }

    public TaskEventType getEvtType()
    {
        return evtType;
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public VarLengthField getTaskType()
    {
        return taskType;
    }

    public VarLengthField getPayload()
    {
        return payload;
    }

    public ListField<TaskHeader> getHeaders()
    {
        return headers;
    }

    public void setEvtType(TaskEventType evtType)
    {
        this.evtType = evtType;
    }

    public void setLockTime(long lockTime)
    {
        this.lockTime = lockTime;
    }

    public class TaskHeader implements DataStuff
    {
        private VarLengthField headerName = new VarLengthField();

        private VarLengthField headerValue = new VarLengthField();

        public VarLengthField getHeaderName()
        {
            return headerName;
        }

        public VarLengthField getHeaderValue()
        {
            return headerValue;
        }

        @Override
        public void reset()
        {
            headerName.reset();
            headerValue.reset();
        }

        @Override
        public int getLength()
        {
            int length = 0;
            length += headerName.getLength();
            length += headerValue.getLength();
            return length;
        }
    }
}
