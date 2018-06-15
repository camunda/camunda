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
package io.zeebe.raft.event;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.raft.Raft;

public class InitialEvent
{
    private static final DirectBuffer EMPTY_OBJECT = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

    public final LogStreamWriter logStreamWriter = new LogStreamWriterImpl();
    public final RecordMetadata metadata = new RecordMetadata();

    public InitialEvent reset()
    {
        logStreamWriter.reset();
        metadata.reset();
        return this;
    }

    public long tryWrite(final Raft raft)
    {
        logStreamWriter.wrap(raft.getLogStream());

        metadata
            .reset()
            .valueType(ValueType.NOOP);

        return logStreamWriter
            .positionAsKey()
            .metadataWriter(metadata)
            .value(EMPTY_OBJECT)
            .tryWrite();
    }

}
