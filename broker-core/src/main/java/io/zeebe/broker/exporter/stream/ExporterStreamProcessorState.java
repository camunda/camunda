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

import io.zeebe.broker.exporter.stream.ExporterRecord.ExporterPosition;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import org.agrona.DirectBuffer;

public class ExporterStreamProcessorState {

  private final DbString exporterId;
  private final DbLong position;
  private final ColumnFamily<DbString, DbLong> exporterPositionColumnFamily;

  public ExporterStreamProcessorState(ZeebeDb<ExporterColumnFamilies> zeebeDb) {
    exporterId = new DbString();
    position = new DbLong();
    exporterPositionColumnFamily =
        zeebeDb.createColumnFamily(ExporterColumnFamilies.DEFAULT, exporterId, position);
  }

  public void setPosition(final String exporterId, final long position) {
    this.exporterId.wrapString(exporterId);
    setPosition(position);
  }

  public void setPosition(final DirectBuffer exporterId, final long position) {
    this.exporterId.wrapBuffer(exporterId);
    setPosition(position);
  }

  private void setPosition(long position) {
    this.position.wrapLong(position);
    exporterPositionColumnFamily.put(this.exporterId, this.position);
  }

  public void setPositionIfGreater(final String exporterId, final long position) {
    this.exporterId.wrapString(exporterId);

    setPositionIfGreater(position);
  }

  public void setPositionIfGreater(final DirectBuffer exporterId, final long position) {
    this.exporterId.wrapBuffer(exporterId);

    setPositionIfGreater(position);
  }

  private void setPositionIfGreater(long position) {
    // not that performant then rocksdb merge but
    // was currently simpler and easier to implement
    // if necessary change it again to merge

    final long oldPosition = getPosition();
    if (oldPosition < position) {
      setPosition(position);
    }
  }

  public long getPosition(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    return getPosition();
  }

  public long getPosition(final DirectBuffer exporterId) {
    this.exporterId.wrapBuffer(exporterId);
    return getPosition();
  }

  private long getPosition() {
    final DbLong zbLong = exporterPositionColumnFamily.get(exporterId);
    return zbLong == null ? ExporterRecord.POSITION_UNKNOWN : zbLong.getValue();
  }

  public ExporterRecord newExporterRecord() {
    final ExporterRecord record = new ExporterRecord();

    exporterPositionColumnFamily.forEach(
        (idOfExporter, currentPosition) -> {
          final ExporterPosition position = record.getPositions().add();
          position.setId(idOfExporter.toString());
          position.setPosition(currentPosition.getValue());
        });

    return record;
  }
}
