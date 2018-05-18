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

import java.util.ArrayList;
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

    public ListSnapshotsResponse(final List<Snapshot> snapshots)
    {
        setSnapshots(snapshots);
    }

    public List<Snapshot> getSnapshots()
    {
        return snapshots;
    }

    public ListSnapshotsResponse setSnapshots(final List<Snapshot> snapshots)
    {
        this.snapshots.clear();
        this.snapshots.addAll(snapshots);
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
        return baseLength + snapshots.stream().mapToInt(Snapshot::getLength).sum();
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

    static class Snapshot
    {
        private String filename;
        private String checksum;
        private long filesize;

        Snapshot()
        {
        }

        Snapshot(final SnapshotsDecoder decoder)
        {
            this();
            decode(decoder);
        }

        public String getFilename()
        {
            return filename;
        }

        public void setFilename(final String filename)
        {
            this.filename = filename;
        }

        public String getChecksum()
        {
            return checksum;
        }

        public void setChecksum(final String checksum)
        {
            this.checksum = checksum;
        }

        public long getFilesize()
        {
            return filesize;
        }

        public void setFilesize(final long filesize)
        {
            this.filesize = filesize;
        }

        public int getLength()
        {
            return sbeBlockLength() +
                    filenameHeaderLength() + filename.getBytes().length +
                    checksumHeaderLength() + checksum.getBytes().length;
        }

        public void encode(final SnapshotsEncoder encoder)
        {
            final byte[] filenameBytes = filename.getBytes();
            final byte[] checksumBytes = checksum.getBytes();

            encoder.next()
                    .filesize(filesize)
                    .putFilename(filenameBytes, 0, filenameBytes.length)
                    .putChecksum(checksumBytes, 0, checksumBytes.length);
        }

        public void decode(final SnapshotsDecoder decoder)
        {
            setFilesize(decoder.filesize());
            setFilename(decoder.filename());
            setChecksum(decoder.checksum());
        }
    }
}
