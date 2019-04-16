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
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.state.StateSnapshotController;
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
public class LogBlockIndex {
  private static final String ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT =
      "Unexpected exception occurred on ensuring maximum snapshot count.";

  public static final int VALUE_NOT_FOUND = -1;
  private long lastVirtualPosition = VALUE_NOT_FOUND;

  private final StateSnapshotController stateSnapshotController;
  private ZeebeDb<LogBlockColumnFamilies> zeebeDb;
  private ColumnFamily<DbLong, DbLong> indexColumnFamily;

  public LogBlockIndex(StateSnapshotController snapshotController) {
    this.stateSnapshotController = snapshotController;
    tryToRestoreAndOpen();
  }

  private void tryToRestoreAndOpen() {
    try {
      lastVirtualPosition = stateSnapshotController.recover();
    } catch (Exception e) {
      Loggers.ROCKSDB_LOGGER.debug("Log block index failed to recover from snapshot", e);
    }

    zeebeDb = stateSnapshotController.openDb();
    indexColumnFamily =
        zeebeDb.createColumnFamily(
            LogBlockColumnFamilies.BLOCK_POSITION_ADDRESS,
            zeebeDb.createContext(),
            new DbLong(),
            new DbLong());
  }

  public void closeDb() throws Exception {
    if (zeebeDb != null) {
      stateSnapshotController.close();
      zeebeDb = null;
      indexColumnFamily = null;
    }
  }

  /**
   * Returns the physical address of the block in which the log entry identified by the provided
   * position resides.
   *
   * @param indexContext the log block index context
   * @param entryPosition a virtual log position
   * @return the physical address of the block containing the log entry identified by the provided
   *     virtual position
   */
  public long lookupBlockAddress(
      final LogBlockIndexContext indexContext, final long entryPosition) {
    final long blockPosition = lookupBlockPosition(indexContext, entryPosition);
    if (blockPosition == VALUE_NOT_FOUND) {
      return VALUE_NOT_FOUND;
    }

    final DbLong dbBlockPosition = indexContext.writeKeyInstance(blockPosition);
    final DbLong address =
        indexColumnFamily.get(
            indexContext.getDbContext(), dbBlockPosition, indexContext.getValueInstance());

    return address != null ? address.getValue() : VALUE_NOT_FOUND;
  }

  /**
   * Returns the position of the first log entry of the the block in which the log entry identified
   * by the provided position resides.
   *
   * @param indexContext the log block index context
   * @param entryPosition a virtual log position
   * @return the position of the block containing the log entry identified by the provided virtual
   *     position
   */
  public long lookupBlockPosition(
      final LogBlockIndexContext indexContext, final long entryPosition) {
    final AtomicLong blockPosition = new AtomicLong(VALUE_NOT_FOUND);

    indexColumnFamily.whileTrue(
        indexContext.getDbContext(),
        (key, val) -> {
          final long currentBlockPosition = key.getValue();

          if (currentBlockPosition <= entryPosition) {
            blockPosition.set(currentBlockPosition);
            return true;
          } else {
            return false;
          }
        },
        indexContext.getKeyInstance(),
        indexContext.getValueInstance());

    return blockPosition.get();
  }

  /**
   * Adds a mapping between a block's position and its address to the log block index.
   *
   * @param indexContext the log block index context
   * @param blockPosition the block's position
   * @param blockAddress the block's address
   */
  public void addBlock(
      final LogBlockIndexContext indexContext, final long blockPosition, final long blockAddress) {
    if (lastVirtualPosition >= blockPosition) {
      final String errorMessage =
          String.format(
              "Illegal value for position.Value=%d, last value in index=%d. Must provide positions in ascending order.",
              blockPosition, lastVirtualPosition);
      throw new IllegalArgumentException(errorMessage);
    }

    final DbLong dbBlockPosition = indexContext.writeKeyInstance(blockPosition);
    final DbLong dbBlockAddress = indexContext.writeValueInstance(blockAddress);

    indexColumnFamily.put(indexContext.getDbContext(), dbBlockPosition, dbBlockAddress);
    lastVirtualPosition = blockPosition;
  }

  /**
   * Deletes mappings up to {@code deletePosition}, with the exception of the last entry in the
   * index, which will not be deleted. Therefore, this method should be used solely as a best-effort
   * attempt to free-up disk space and not as a dependable delete operation.
   *
   * @param indexContext the log block index context
   * @param deletePosition the position up to which entries will be deleted
   */
  public void deleteUpToPosition(
      final LogBlockIndexContext indexContext, final long deletePosition) {
    final AtomicLong lastBlockPosition = new AtomicLong(VALUE_NOT_FOUND);

    indexColumnFamily.whileTrue(
        indexContext.getDbContext(),
        (key, val) -> {
          final long storedBlockPosition = key.getValue();

          if (storedBlockPosition <= deletePosition) {
            if (lastBlockPosition.get() != VALUE_NOT_FOUND) {
              deleteEntry(indexContext, lastBlockPosition.get());
            }

            lastBlockPosition.set(storedBlockPosition);
            return true;
          }

          return false;
        },
        indexContext.getKeyInstance(),
        indexContext.getValueInstance());
  }

  private void deleteEntry(final LogBlockIndexContext indexContext, final long blockPosition) {
    final DbLong dbBlockPosition = indexContext.writeKeyInstance(blockPosition);
    indexColumnFamily.delete(indexContext.getDbContext(), dbBlockPosition);
  }

  /**
   * Checks if the log block index has entries.
   *
   * @param indexContext the log block index context
   * @return <code>true</code> if the index has no entry
   */
  public boolean isEmpty(final LogBlockIndexContext indexContext) {
    return indexColumnFamily.isEmpty(indexContext.getDbContext());
  }

  /**
   * Writes a snapshot with the provided position as last written position
   *
   * @param snapshotEventPosition last written position
   */
  public void writeSnapshot(final long snapshotEventPosition, final int maxSnapshots) {
    stateSnapshotController.takeSnapshot(snapshotEventPosition);

    try {
      stateSnapshotController.ensureMaxSnapshotCount(maxSnapshots);
    } catch (Exception e) {
      Loggers.SNAPSHOT_LOGGER.error(ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT, e);
    }
  }

  /**
   * Returns the last position written to the index or read from a snapshot.
   *
   * @return the last written position
   */
  public long getLastPosition() {
    return lastVirtualPosition;
  }

  /**
   * Returns a log block index context which contain the required state to use the index in a
   * thread-safe manner, including the DbContext required to interact with the database.
   *
   * @return a newly created log block index context
   */
  public LogBlockIndexContext createLogBlockIndexContext() {
    return new LogBlockIndexContext(zeebeDb.createContext());
  }
}
