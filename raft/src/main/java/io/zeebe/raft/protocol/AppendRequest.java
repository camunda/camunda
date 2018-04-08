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

import static io.zeebe.raft.AppendRequestDecoder.*;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.*;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class AppendRequest extends AbstractRaftMessage implements HasSocketAddress, HasTerm, HasPartition
{

    protected final AppendRequestDecoder bodyDecoder = new AppendRequestDecoder();
    protected final AppendRequestEncoder bodyEncoder = new AppendRequestEncoder();

    // read + write
    protected int partitionId;
    protected int term;
    protected long previousEventPosition;
    protected int previousEventTerm;
    protected long commitPosition;

    // read
    protected final DirectBuffer readHost = new UnsafeBuffer(0, 0);
    protected final SocketAddress readSocketAdress = new SocketAddress();
    protected final DirectBuffer readData = new UnsafeBuffer(0, 0);
    protected final LoggedEventImpl readEvent = new LoggedEventImpl();

    // write
    private SocketAddress writeSocketAddress;
    private LoggedEventImpl writeEvent;

    public AppendRequest()
    {
        reset();
    }

    public AppendRequest reset()
    {
        partitionId = partitionIdNullValue();
        term = termNullValue();
        previousEventPosition = previousEventPositionNullValue();
        previousEventTerm = previousEventTermNullValue();
        commitPosition = commitPositionNullValue();

        readHost.wrap(0, 0);
        readSocketAdress.reset();
        readData.wrap(0, 0);
        readEvent.wrap(null, -1);

        writeSocketAddress = null;
        writeEvent = null;

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

    public long getPreviousEventPosition()
    {
        return previousEventPosition;
    }

    public AppendRequest setPreviousEventPosition(final long previousEventPosition)
    {
        this.previousEventPosition = previousEventPosition;
        return this;
    }

    public int getPreviousEventTerm()
    {
        return previousEventTerm;
    }

    public AppendRequest setPreviousEventTerm(final int previousEventTerm)
    {
        this.previousEventTerm = previousEventTerm;
        return this;
    }

    public long getCommitPosition()
    {
        return commitPosition;
    }

    @Override
    public SocketAddress getSocketAddress()
    {
        return readSocketAdress;
    }

    public LoggedEventImpl getEvent()
    {
        if (readEvent.getBuffer() != null)
        {
            return readEvent;
        }
        else
        {
            return null;
        }
    }

    public AppendRequest setEvent(final LoggedEventImpl event)
    {
        writeEvent = event;
        return this;
    }

    public AppendRequest setRaft(final Raft raft)
    {
        final LogStream logStream = raft.getLogStream();

        partitionId = logStream.getPartitionId();
        term = raft.getTerm();
        commitPosition = logStream.getCommitPosition();

        writeSocketAddress = raft.getSocketAddress();

        return this;
    }

    @Override
    public int getLength()
    {
        int length = headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            hostHeaderLength() +
            dataHeaderLength();

        if (writeSocketAddress != null)
        {
            length += writeSocketAddress.hostLength();
        }

        if (writeEvent != null)
        {
            length += writeEvent.getFragmentLength();
        }

        return length;
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
        previousEventPosition = bodyDecoder.previousEventPosition();
        previousEventTerm = bodyDecoder.previousEventTerm();
        commitPosition = bodyDecoder.commitPosition();
        readSocketAdress.port(bodyDecoder.port());

        offset += bodyDecoder.sbeBlockLength();

        offset += wrapVarData(buffer, offset, readHost, hostHeaderLength(), bodyDecoder.hostLength());
        bodyDecoder.limit(offset);

        readSocketAdress.host(readHost, 0, readHost.capacity());

        offset += wrapVarData(buffer, offset, readData, dataHeaderLength(), bodyDecoder.dataLength());
        bodyDecoder.limit(offset);

        if (readData.capacity() > 0)
        {
            readEvent.wrap(readData, 0);
        }

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder
            .wrap(buffer, offset)
            .partitionId(partitionId)
            .term(term)
            .previousEventPosition(previousEventPosition)
            .previousEventTerm(previousEventTerm)
            .commitPosition(commitPosition);

        if (writeSocketAddress != null)
        {
            bodyEncoder
                .port(writeSocketAddress.port())
                .putHost(writeSocketAddress.getHostBuffer(), 0, writeSocketAddress.hostLength());
        }

        if (writeEvent != null)
        {
            bodyEncoder
                .putData(writeEvent.getBuffer(), writeEvent.getFragmentOffset(), writeEvent.getFragmentLength());
        }

    }
}
