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

import io.zeebe.raft.BooleanType;
import io.zeebe.raft.ConfigurationResponseDecoder;
import io.zeebe.raft.ConfigurationResponseEncoder;
import io.zeebe.raft.Raft;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.util.ArrayList;
import java.util.List;

import static io.zeebe.raft.ConfigurationResponseEncoder.MembersEncoder.*;
import static io.zeebe.raft.ConfigurationResponseEncoder.termNullValue;

public class ConfigurationResponse extends AbstractRaftMessage implements HasTerm
{

    protected final ConfigurationResponseDecoder bodyDecoder = new ConfigurationResponseDecoder();
    protected final ConfigurationResponseEncoder bodyEncoder = new ConfigurationResponseEncoder();

    // read + write
    protected int term;
    protected boolean succeeded;

    // read
    protected List<SocketAddress> readMembers = new ArrayList<>();

    // write
    protected List<SocketAddress> writeMembers = new ArrayList<>();

    public ConfigurationResponse()
    {
        reset();
    }

    public ConfigurationResponse reset()
    {
        term = termNullValue();
        succeeded = false;

        readMembers.clear();

        writeMembers.clear();

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
    public int getTerm()
    {
        return term;
    }

    public boolean isSucceeded()
    {
        return succeeded;
    }

    public ConfigurationResponse setSucceeded(final boolean succeeded)
    {
        this.succeeded = succeeded;
        return this;
    }

    public List<SocketAddress> getMembers()
    {
        return readMembers;
    }

    public ConfigurationResponse setRaft(final Raft raft)
    {
        term = raft.getTerm();
        writeMembers.add(raft.getSocketAddress());
        for (int i = 0; i < raft.getMemberSize(); i++)
        {
            writeMembers.add(raft.getMember(i).getRemoteAddress().getAddress());
        }

        return this;
    }

    @Override
    public int getLength()
    {
        final int membersCount = writeMembers.size();

        int length = headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * membersCount;

        for (int i = 0; i < membersCount; i++)
        {
            length += writeMembers.get(i).hostLength();
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

        term = bodyDecoder.term();
        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;

        for (final ConfigurationResponseDecoder.MembersDecoder decoder : bodyDecoder.members())
        {
            final SocketAddress socketAddress = new SocketAddress();
            socketAddress.port(decoder.port());

            final int hostLength = decoder.hostLength();
            final MutableDirectBuffer endpointBuffer = socketAddress.getHostBuffer();
            socketAddress.hostLength(hostLength);
            decoder.getHost(endpointBuffer, 0, hostLength);

            // TODO: make this garbage free or not!?
            readMembers.add(socketAddress);
        }

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
                   .term(term)
                   .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE);

        final int membersCount = writeMembers.size();

        final ConfigurationResponseEncoder.MembersEncoder encoder = bodyEncoder.membersCount(membersCount);
        for (int i = 0; i < membersCount; i++)
        {
            final SocketAddress socketAddress = writeMembers.get(i);
            encoder.next()
                   .port(socketAddress.port())
                   .putHost(socketAddress.getHostBuffer(), 0, socketAddress.hostLength());
        }
    }
}
