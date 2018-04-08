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

import static io.zeebe.raft.VoteResponseEncoder.termNullValue;

import io.zeebe.raft.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class VoteResponse extends AbstractRaftMessage implements HasTerm
{

    private final VoteResponseDecoder bodyDecoder = new VoteResponseDecoder();
    private final VoteResponseEncoder bodyEncoder = new VoteResponseEncoder();

    private int term;
    private boolean granted;

    public VoteResponse()
    {
        reset();
    }

    public VoteResponse reset()
    {
        term = termNullValue();
        granted = false;

        return this;
    }

    @Override
    public int getTerm()
    {
        return term;
    }

    public VoteResponse setTerm(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean isGranted()
    {
        return granted;
    }

    public VoteResponse setGranted(final boolean granted)
    {
        this.granted = granted;
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
    public int getLength()
    {
        return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();

    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        term = bodyDecoder.term();
        granted = bodyDecoder.granted() == BooleanType.TRUE;

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
                   .granted(granted ? BooleanType.TRUE : BooleanType.FALSE);
    }

}
