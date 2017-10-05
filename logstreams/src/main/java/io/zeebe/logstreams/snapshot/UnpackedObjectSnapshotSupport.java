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
package io.zeebe.logstreams.snapshot;

import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.ExpandableArrayBuffer;

import io.zeebe.logstreams.spi.ComposableSnapshotSupport;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.util.StreamUtil;

public class UnpackedObjectSnapshotSupport implements ComposableSnapshotSupport
{

    protected UnpackedObject object;
    protected MsgPackWriter writer = new MsgPackWriter();
    protected MsgPackReader reader = new MsgPackReader();

    /*
     * This implementation via an intermediate buffer is not the most efficient, because
     * it requires an extra intermediate copy of the serialized value; directly writing/reading to/from the
     * streams would be more efficient, but extending MsgPackReader/Writer is no quick win
     * (=> reading primitives instead of single bytes; byte order; skipping values efficiently).
     *
     * This implementation should be ok for rather small objects.
     */
    protected ExpandableArrayBuffer ioBuffer = new ExpandableArrayBuffer();

    public UnpackedObjectSnapshotSupport(UnpackedObject object)
    {
        this.object = object;
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        writer.wrap(ioBuffer, 0);
        final int length = object.getEncodedLength();
        object.write(writer);
        outputStream.write(ioBuffer.byteArray(), 0, length);
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        final int length = StreamUtil.read(inputStream, ioBuffer, 0);
        reader.wrap(ioBuffer, 0, length);
        object.read(reader);
    }

    @Override
    public void reset()
    {
        object.reset();
    }

    @Override
    public long snapshotSize()
    {
        return object.getEncodedLength();
    }
}
