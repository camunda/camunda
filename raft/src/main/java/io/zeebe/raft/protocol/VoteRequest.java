/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft.protocol;

import static io.zeebe.raft.VoteRequestEncoder.*;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Raft;
import io.zeebe.raft.VoteRequestDecoder;
import io.zeebe.raft.VoteRequestEncoder;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class VoteRequest extends AbstractRaftMessage implements HasSocketAddress, HasTerm, HasTopic
{

    private final VoteRequestDecoder bodyDecoder = new VoteRequestDecoder();
    private final VoteRequestEncoder bodyEncoder = new VoteRequestEncoder();

    // read + write
    private int partitionId;
    private int term;
    private long lastEventPosition;
    private int lastEventTerm;

    // read
    private final DirectBuffer readTopicName = new UnsafeBuffer(0, 0);
    private final DirectBuffer readHost = new UnsafeBuffer(0, 0);
    private final SocketAddress readSocketAddress = new SocketAddress();

    // write
    private DirectBuffer writeTopicName;
    private SocketAddress writeSocketAddress;

    public VoteRequest()
    {
        reset();
    }

    public VoteRequest reset()
    {
        partitionId = partitionIdNullValue();
        term = termNullValue();
        lastEventPosition = lastEventPositionNullValue();
        lastEventTerm = lastEventTermNullValue();

        readTopicName.wrap(0, 0);
        readHost.wrap(0, 0);
        readSocketAddress.reset();

        writeTopicName = null;
        writeSocketAddress = null;

        return this;
    }

    @Override
    public DirectBuffer getTopicName()
    {
        return readTopicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public int getTerm()
    {
        return term;
    }

    @Override
    public SocketAddress getSocketAddress()
    {
        return readSocketAddress;
    }

    public long getLastEventPosition()
    {
        return lastEventPosition;
    }

    public VoteRequest setLastEventPosition(final long lastEventPosition)
    {
        this.lastEventPosition = lastEventPosition;
        return this;
    }

    public int getLastEventTerm()
    {
        return lastEventTerm;
    }

    public VoteRequest setLastEventTerm(final int lastEventTerm)
    {
        this.lastEventTerm = lastEventTerm;
        return this;
    }

    @Override
    protected int getVersion()
    {
        return bodyDecoder.sbeSchemaVersion();
    }

    @Override
    protected int getSchemaId()
    {
        return bodyDecoder.sbeSchemaId();
    }

    @Override
    protected int getTemplateId()
    {
        return bodyDecoder.sbeTemplateId();
    }

    public VoteRequest setRaft(final Raft raft)
    {
        final LogStream logStream = raft.getLogStream();

        writeTopicName = logStream.getTopicName();
        partitionId = logStream.getPartitionId();
        term = raft.getTerm();

        writeSocketAddress = raft.getSocketAddress();

        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            topicNameHeaderLength() +
            writeTopicName.capacity() +
            hostHeaderLength() +
            writeSocketAddress.hostLength();
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        reset();

        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
        term = bodyDecoder.term();
        lastEventPosition = bodyDecoder.lastEventPosition();
        lastEventTerm = bodyDecoder.lastEventTerm();

        readSocketAddress.port(bodyDecoder.port());

        offset += bodyDecoder.sbeBlockLength();

        offset += wrapVarData(buffer, offset, readTopicName, topicNameHeaderLength(), bodyDecoder.topicNameLength());
        bodyDecoder.limit(offset);

        offset += wrapVarData(buffer, offset, readHost, hostHeaderLength(), bodyDecoder.hostLength());
        bodyDecoder.limit(offset);

        readSocketAddress.host(readHost, 0, readHost.capacity());

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .partitionId(partitionId)
            .term(term)
            .lastEventPosition(lastEventPosition)
            .lastEventTerm(lastEventTerm)
            .port(writeSocketAddress.port())
            .putTopicName(writeTopicName, 0, writeTopicName.capacity())
            .putHost(writeSocketAddress.getHostBuffer(), 0, writeSocketAddress.hostLength());
    }
}
