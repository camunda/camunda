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
package io.zeebe.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.impl.log.index.LogBlockColumnFamilies;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.impl.log.index.LogBlockIndexContext;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class LogBlockIndexTest {

  public static final int ADDRESS_MULTIPLIER = 200;
  public static final int ENTRY_OFFSET = 5;

  private LogBlockIndex blockIndex;
  private LogBlockIndexContext indexContext;

  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public TemporaryFolder runtimeDirectory = new TemporaryFolder();
  @Rule public TemporaryFolder snapshotDirectory = new TemporaryFolder();

  @Before
  public void setup() throws Exception {
    runtimeDirectory.create();
    snapshotDirectory.create();

    startBlockIndexDb();
  }

  private void startBlockIndexDb() {
    final ZeebeDbFactory<LogBlockColumnFamilies> dbFactory =
        ZeebeRocksDbFactory.newFactory(LogBlockColumnFamilies.class);
    final StateStorage stateStorage =
        new StateStorage(runtimeDirectory.getRoot(), snapshotDirectory.getRoot());
    final StateSnapshotController controller = new StateSnapshotController(dbFactory, stateStorage);

    blockIndex = new LogBlockIndex(controller);
    indexContext = blockIndex.createLogBlockIndexContext();
  }

  @After
  public void tearDown() {
    runtimeDirectory.delete();
    snapshotDirectory.delete();

    try {
      blockIndex.closeDb();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void shouldAddBlocks() {
    final int numBlocks = 10;

    // when
    final long lastPosition = addBlocks(numBlocks);

    // then
    lookupAndAssert(numBlocks);
    assertThat(blockIndex.isEmpty(indexContext)).isFalse();
    assertThat(blockIndex.getLastPosition()).isEqualTo(lastPosition);
  }

  @Test
  public void shouldNotAddBlockWithEqualPos() {
    // given
    blockIndex.addBlock(indexContext, 10, 0);

    // then
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Illegal value for position");

    // when
    blockIndex.addBlock(indexContext, 10, 0);
  }

  @Test
  public void shouldNotAddBlockWithSmallerPos() {
    // given
    blockIndex.addBlock(indexContext, 10, 0);

    // then
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Illegal value for position");

    // when
    blockIndex.addBlock(indexContext, 9, 0);
  }

  @Test
  public void shouldReturnMinusOneForEmptyBlockIndex() {
    assertThat(blockIndex.lookupBlockAddress(indexContext, -1)).isEqualTo(-1);
    assertThat(blockIndex.lookupBlockAddress(indexContext, 1)).isEqualTo(-1);
  }

  @Test
  public void shouldNotReturnFirstBlockAddress() {
    // given
    blockIndex.addBlock(indexContext, 10, 1000);

    // then
    for (int i = 0; i < 10; i++) {
      assertThat(blockIndex.lookupBlockAddress(indexContext, i)).isEqualTo(-1);
    }
  }

  @Test
  public void shouldReturnFirstBlockAddress() {
    // given
    blockIndex.addBlock(indexContext, 10, 1000);

    // then
    for (int i = 10; i < 100; i++) {
      assertThat(blockIndex.lookupBlockAddress(indexContext, i)).isEqualTo(1000);
    }
  }

  @Test
  public void shouldLookupBlockAddresses() {
    final int capacity = 100;

    // given
    for (int i = 0; i < capacity; i++) {
      final int pos = (i + 1) * 10;
      final int addr = (i + 1) * 100;

      blockIndex.addBlock(indexContext, pos, addr);
    }

    // then
    for (int i = 0; i < capacity; i++) {
      final int expectedAddr = (i + 1) * 100;

      for (int j = 0; j < 10; j++) {
        final int pos = ((i + 1) * 10) + j;

        assertThat(blockIndex.lookupBlockAddress(indexContext, pos)).isEqualTo(expectedAddr);
      }
    }
  }

  @Test
  public void shouldNotReturnFirstBlockPosition() {
    // given
    blockIndex.addBlock(indexContext, 10, 1000);

    // then
    for (int i = 0; i < 10; i++) {
      assertThat(blockIndex.lookupBlockPosition(indexContext, i)).isEqualTo(-1);
    }
  }

  @Test
  public void shouldReturnFirstBlockPosition() {
    // given
    blockIndex.addBlock(indexContext, 10, 1000);

    // then
    for (int i = 10; i < 100; i++) {
      assertThat(blockIndex.lookupBlockPosition(indexContext, i)).isEqualTo(10);
    }
  }

  @Test
  public void shouldLookupBlockPositions() {
    final int capacity = 100;

    // given
    for (int i = 0; i < capacity; i++) {
      final int pos = (i + 1) * 10;
      final int addr = (i + 1) * 100;

      blockIndex.addBlock(indexContext, pos, addr);
    }

    // then
    for (int i = 0; i < capacity; i++) {
      final int expectedPos = (i + 1) * 10;

      for (int j = 0; j < 10; j++) {
        final int pos = ((i + 1) * 10) + j;

        assertThat(blockIndex.lookupBlockPosition(indexContext, pos)).isEqualTo(expectedPos);
      }
    }
  }

  @Test
  public void shouldRecoverIndexFromSnapshot() throws Exception {
    // given
    final int numBlocks = 10;
    final long snapshotPosition = addBlocks(numBlocks);
    blockIndex.writeSnapshot(snapshotPosition, 1);

    // when
    blockIndex.closeDb(); // close and reopen DB
    startBlockIndexDb();

    // then
    lookupAndAssert(numBlocks);
    assertThat(blockIndex.getLastPosition()).isEqualTo(snapshotPosition);
  }

  @Test
  public void shouldDeleteEntriesUpToPosition() {
    // given
    final int numBlocks = 3;
    addBlocks(numBlocks);

    // when
    blockIndex.deleteUpToPosition(indexContext, ENTRY_OFFSET);

    // then
    assertThat(blockIndex.lookupBlockAddress(indexContext, 0))
        .isEqualTo(LogBlockIndex.VALUE_NOT_FOUND);
    for (int i = 0; i < ENTRY_OFFSET; ++i) {
      assertThat(blockIndex.lookupBlockPosition(indexContext, i))
          .isEqualTo(LogBlockIndex.VALUE_NOT_FOUND);
    }
    assertThat(blockIndex.lookupBlockAddress(indexContext, ENTRY_OFFSET))
        .isEqualTo(ENTRY_OFFSET * ADDRESS_MULTIPLIER);
    // don't delete block if it may contain entries that shouldn't be deleted
  }

  // Adds blocks and returns the last added position
  private long addBlocks(int numBlocks) {
    for (int blockPos = 0; blockPos < numBlocks * ENTRY_OFFSET; blockPos += ENTRY_OFFSET) {
      final int address = blockPos * ADDRESS_MULTIPLIER;
      blockIndex.addBlock(indexContext, blockPos, address);
    }

    return (numBlocks - 1) * ENTRY_OFFSET;
  }

  private void lookupAndAssert(int numBlocks) {
    for (int blockPos = 0; blockPos < numBlocks * ENTRY_OFFSET; blockPos += ENTRY_OFFSET) {
      final int address = blockPos * ADDRESS_MULTIPLIER;

      for (int entryPos = blockPos; entryPos < blockPos + ENTRY_OFFSET; entryPos++) {
        assertThat(blockIndex.lookupBlockPosition(indexContext, entryPos)).isEqualTo(blockPos);
        assertThat(blockIndex.lookupBlockAddress(indexContext, entryPos)).isEqualTo(address);
      }
    }
  }
}
