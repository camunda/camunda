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
package io.zeebe.broker.clustering.api;

import static io.zeebe.clustering.management.ListSnapshotsResponseDecoder.SnapshotsDecoder;
import static io.zeebe.clustering.management.ListSnapshotsResponseEncoder.SnapshotsEncoder;
import static io.zeebe.clustering.management.ListSnapshotsResponseEncoder.SnapshotsEncoder.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.zeebe.clustering.management.ListSnapshotsResponseDecoder;
import io.zeebe.clustering.management.ListSnapshotsResponseEncoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ListSnapshotsResponse implements BufferWriter, BufferReader
{
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ListSnapshotsResponseDecoder bodyDecoder = new ListSnapshotsResponseDecoder();

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final ListSnapshotsResponseEncoder bodyEncoder = new ListSnapshotsResponseEncoder();

    private final List<Snapshot> snapshots = new ArrayList<>();

    public ListSnapshotsResponse()
    {
    }

    public List<Snapshot> getSnapshots()
    {
        return snapshots;
    }

    public ListSnapshotsResponse addSnapshot(final String name, final long logPosition, final byte[] checksum, final long length)
    {
        this.snapshots.add(new Snapshot(name, logPosition, checksum, length));
        return this;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        snapshots.clear();
        bodyDecoder.snapshots().forEach((decoder) -> snapshots.add(new Snapshot(decoder)));
    }

    @Override
    public int getLength()
    {
        final int baseLength = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength() + sbeHeaderSize();
        return baseLength + snapshots.stream().mapToInt(Snapshot::getEncodedLength).sum();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
                .blockLength(bodyEncoder.sbeBlockLength())
                .templateId(bodyEncoder.sbeTemplateId())
                .schemaId(bodyEncoder.sbeSchemaId())
                .version(bodyEncoder.sbeSchemaVersion());

        final int snapshotsCount = snapshots.size();
        final SnapshotsEncoder encoder = bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
                .snapshotsCount(snapshotsCount);

        snapshots.forEach((snapshot) -> snapshot.encode(encoder));
    }

    public static class Snapshot
    {
        private String name;
        private byte[] checksum;
        private long length;
        private long logPosition;

        Snapshot(final String name, final long logPosition, final byte[] checksum, final long length)
        {
            this.name = name;
            this.checksum = checksum;
            this.length = length;
            this.logPosition = logPosition;
        }

        Snapshot(final SnapshotsDecoder decoder)
        {
            decode(decoder);
        }

        public String getName()
        {
            return name;
        }

        public void setName(final String name)
        {
            this.name = name;
        }

        public byte[] getChecksum()
        {
            return checksum;
        }

        public void setChecksum(final byte[] checksum)
        {
            this.checksum = checksum;
        }

        public long getLength()
        {
            return length;
        }

        public void setLength(final long length)
        {
            this.length = length;
        }

        public long getLogPosition()
        {
            return logPosition;
        }

        public void setLogPosition(long logPosition)
        {
            this.logPosition = logPosition;
        }

        public int getEncodedLength()
        {
            return sbeBlockLength() +
                    nameHeaderLength() + name.getBytes().length +
                    checksumHeaderLength() + checksum.length;
        }

        void encode(final SnapshotsEncoder encoder)
        {
            final byte[] nameBytes;
            try
            {
                nameBytes = name.getBytes(SnapshotsEncoder.nameCharacterEncoding());
            }
            catch (final UnsupportedEncodingException ex)
            {
                throw new RuntimeException(ex);
            }

            encoder.next()
                    .length(length)
                    .logPosition(logPosition)
                    .putName(nameBytes, 0, nameBytes.length)
                    .putChecksum(checksum, 0, checksum.length);
        }

        void decode(final SnapshotsDecoder decoder)
        {
            setLength(decoder.length());
            setName(decoder.name());
            setLogPosition(decoder.logPosition());

            checksum = new byte[decoder.checksumLength()];
            decoder.getChecksum(checksum, 0, checksum.length);
        }

        @Override
        public String toString()
        {
            return "Snapshot{" + "name='" + name + '\'' +
                    ", checksum=" + Arrays.toString(checksum) +
                    ", length=" + length + ", logPosition=" + logPosition +
                    '}';
        }

        @Override
        public int hashCode()
        {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object other)
        {
            if (other instanceof Snapshot)
            {
                final Snapshot snapshot = (Snapshot)other;
                return name.equals(snapshot.getName()) &&
                       logPosition == snapshot.getLogPosition() &&
                       Arrays.equals(checksum, snapshot.getChecksum()) &&
                       length == snapshot.getLength();
            }
            else
            {
                return super.equals(other);
            }
        }
    }
}
