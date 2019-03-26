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
package io.zeebe.logstreams.impl.log.index;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.DbLong;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import io.zeebe.logstreams.state.StateStorage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Block index, mapping an event's position to the physical address of the block in which it resides
 * in storage.
 *
 * <p>Each Event has a position inside the stream. This position addresses it uniquely and is
 * assigned when the entry is first published to the stream. The position never changes and is
 * preserved through maintenance operations like compaction.
 *
 * <p>In order to read an event, the position must be translated into the "physical address" of the
 * block in which it resides in storage. Then, the block can be scanned for the event position
 * requested.
 */
public class LogBlockIndex implements SnapshotSupport {

  private final StateSnapshotController stateSnapshotController;
  private ColumnFamily<DbLong, DbLong> blockPositionToAddress;

  private final DbLong blockPosition = new DbLong();
  private final DbLong value = new DbLong();
  private ZeebeDb db;
  private DbContext context;

  private long lastVirtualPosition = -1;

  public LogBlockIndex(
      ZeebeDbFactory<LogBlockColumnFamilies> dbFactory, StateStorage stateStorage) {
    this.stateSnapshotController = new StateSnapshotController(dbFactory, stateStorage);
  }

  public void openDb() {
    db = stateSnapshotController.openDb();
    context = db.createContext();
    blockPositionToAddress =
        db.createColumnFamily(
            LogBlockColumnFamilies.BLOCK_POSITION_ADDRESS, context, blockPosition, value);
  }

  public void closeDb() throws Exception {
    if (db != null) {
      stateSnapshotController.close();
      db = null;
    }
  }

  /**
   * Returns the physical address of the block in which the log entry identified by the provided
   * position resides.
   *
   * @param entryPosition a virtual log position
   * @return the physical address of the block containing the log entry identified by the provided
   *     virtual position
   */
  public synchronized long lookupBlockAddress(final long entryPosition) {
    final long blockPosition = lookupBlockPosition(entryPosition);
    if (blockPosition == -1) {
      return -1;
    }

    this.blockPosition.wrapLong(blockPosition);
    final DbLong address = blockPositionToAddress.get(this.blockPosition);

    return address != null ? address.getValue() : -1;
  }

  /**
   * Returns the position of the first log entry of the the block in which the log entry identified
   * by the provided position resides.
   *
   * @param entryPosition a virtual log position
   * @return the position of the block containing the log entry identified by the provided virtual
   *     position
   */
  public synchronized long lookupBlockPosition(final long entryPosition) {
    final AtomicLong blockPosition = new AtomicLong(-1);

    blockPositionToAddress.whileTrue(
        (key, val) -> {
          final long currentBlockPosition = key.getValue();

          if (currentBlockPosition <= entryPosition) {
            blockPosition.set(currentBlockPosition);
            return true;
          } else {
            return false;
          }
        });

    return blockPosition.get();
  }

  public synchronized void addBlock(long blockPosition, long blockAddress) {
    if (lastVirtualPosition >= blockPosition) {
      final String errorMessage =
          String.format(
              "Illegal value for position.Value=%d, last value in index=%d. Must provide positions in ascending order.",
              blockPosition, lastVirtualPosition);
      throw new IllegalArgumentException(errorMessage);
    }

    lastVirtualPosition = blockPosition;
    this.blockPosition.wrapLong(blockPosition);
    value.wrapLong(blockAddress);

    blockPositionToAddress.put(this.blockPosition, value);
  }

  @Override
  public void writeSnapshot(final long snapshotEventPosition) {
    final StateSnapshotMetadata snapshotMetadata = new StateSnapshotMetadata(snapshotEventPosition);
    stateSnapshotController.takeSnapshot(snapshotMetadata);
  }

  @Override
  public void recoverFromSnapshot() throws Exception {
    final StateSnapshotMetadata snapshotMetadata =
        stateSnapshotController.recoverFromLatestSnapshot();
    lastVirtualPosition = snapshotMetadata.getLastWrittenEventPosition();
  }

  public synchronized boolean isEmpty() {
    return blockPositionToAddress.isEmpty();
  }

  public long getLastPosition() {
    return lastVirtualPosition;
  }
}
