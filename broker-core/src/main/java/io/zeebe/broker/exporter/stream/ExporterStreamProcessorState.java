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
package io.zeebe.broker.exporter.stream;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;
import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.broker.exporter.stream.ExporterRecord.ExporterPosition;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.util.LangUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public class ExporterStreamProcessorState extends StateController {
  private final ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);

  public void setPosition(final String exporterId, final long position) {
    setPosition(toByteArray(exporterId), position);
  }

  public void setPosition(final DirectBuffer exporterId, final long position) {
    setPosition(toByteArray(exporterId), position);
  }

  public void setPosition(final byte[] exporterId, final long position) {
    final byte[] value = ofLong(longBuffer, position);

    try {
      getDb().put(exporterId, value);
    } catch (RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void setPositionIfGreater(final String exporterId, final long position) {
    setPositionIfGreater(toByteArray(exporterId), position);
  }

  public void setPositionIfGreater(final DirectBuffer exporterId, final long position) {
    setPositionIfGreater(toByteArray(exporterId), position);
  }

  public void setPositionIfGreater(final byte[] exporterId, final long position) {
    final byte[] value = ofLong(longBuffer, position);

    try {
      getDb().merge(exporterId, value);
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public long getPosition(final String exporterId) {
    return getPosition(BufferUtil.wrapString(exporterId));
  }

  public long getPosition(final DirectBuffer exporterId) {
    return getPosition(toByteArray(exporterId));
  }

  public long getPosition(final byte[] exporterId) {
    long position = ExporterRecord.POSITION_UNKNOWN;

    try {
      final int bytesRead = getDb().get(exporterId, longBuffer.array());
      if (bytesRead == Long.BYTES) {
        position = toLong(longBuffer);
      }
    } catch (RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }

    return position;
  }

  public ExporterRecord newExporterRecord() {
    final ExporterRecord record = new ExporterRecord();

    try (RocksIterator iterator = getDb().newIterator()) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        final ExporterPosition position = record.getPositions().add();
        final long value = toLong(ByteBuffer.wrap(iterator.value()));

        position.setId(BufferUtil.wrapArray(iterator.key()));
        position.setPosition(value);
      }
    }

    return record;
  }

  private byte[] ofLong(final ByteBuffer buffer, final long value) {
    buffer.order(STATE_BYTE_ORDER).putLong(0, value);
    return buffer.array();
  }

  private long toLong(final ByteBuffer buffer) {
    return buffer.order(STATE_BYTE_ORDER).getLong(0);
  }

  @Override
  protected Options createOptions() {
    return super.createOptions().setMergeOperatorName("max");
  }

  private byte[] toByteArray(final DirectBuffer value) {
    return BufferUtil.bufferAsArray(value);
  }

  private byte[] toByteArray(final String value) {
    return getBytes(value);
  }
}
