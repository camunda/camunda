/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.api.BackupIndex.IndexedBackup;
import io.camunda.zeebe.backup.index.CompactBackupIndex.IndexCorruption;
import io.camunda.zeebe.backup.index.CompactBackupIndex.PartialIndexCorruption;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class CompactBackupIndexTest {

  @TempDir private Path tempDir;

  private IndexedBackup createBackup(
      final long checkpointId, final long firstLogPos, final long checkpointPosition) {
    return new IndexedBackup(checkpointId, firstLogPos, checkpointPosition);
  }

  @Nested
  class BasicOperations {

    @Test
    void shouldCreateNewIndexFile() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        // then
        assertThat(indexFile).exists();
        assertThat(Files.size(indexFile)).isGreaterThan(8); // Header size (2*Integer.BYTES)
      }
    }

    @Test
    void shouldOpenExistingIndexFile() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var backup = createBackup(1, 100L, 200L);
      final var indexFile = tempDir.resolve("backup.index");
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup);
      }

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        // then
        assertThat(index.all()).singleElement().isEqualTo(backup);
      }
    }

    @Test
    void shouldReturnEmptyStreamWhenNoBackups()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        // then
        assertThat(index.all()).isEmpty();
      }
    }
  }

  @Nested
  class AddingBackups {

    @Test
    void shouldAddSingleBackup() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup = createBackup(1, 100L, 200L);

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup);

        // then
        assertThat(index.all()).singleElement().isEqualTo(backup);
      }
    }

    @Test
    void shouldAddMultipleBackupsInOrder()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        final var backup1 = createBackup(1, 100L, 200L);
        final var backup2 = createBackup(2, 200L, 300L);
        final var backup3 = createBackup(3, 300L, 400L);
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // then
        assertThat(index.all()).containsExactly(backup1, backup2, backup3);
      }
    }

    @Test
    void shouldAddBackupsOutOfOrderAndMaintainSorting()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        final var backup3 = createBackup(3, 300L, 400L);
        final var backup1 = createBackup(1, 100L, 200L);
        final var backup5 = createBackup(5, 500L, 600L);
        final var backup2 = createBackup(2, 200L, 300L);
        final var backup4 = createBackup(4, 400L, 500L);
        index.add(backup3);
        index.add(backup1);
        index.add(backup5);
        index.add(backup2);
        index.add(backup4);

        // then
        assertThat(index.all()).containsExactly(backup1, backup2, backup3, backup4, backup5);
      }
    }

    @Test
    void shouldNotAddDuplicateBackup() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        final var backup1 = createBackup(1, 100L, 200L);
        final var duplicate1 = createBackup(1, 100L, 200L); // Same as backup1
        final var duplicate2 = createBackup(1, 999L, 999L); // Different descriptor
        index.add(backup1);
        index.add(duplicate1);
        index.add(duplicate2);

        // then
        assertThat(index.all())
            .singleElement()
            .isEqualTo(backup1); // Original values, duplicate not added
      }
    }

    @Test
    void shouldHandleInsertionAtBeginning()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup5 = createBackup(5, 500L, 600L);
      final var backup10 = createBackup(10, 1000L, 1100L);
      final var backup15 = createBackup(15, 1500L, 1600L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup5);
        index.add(backup10);
        index.add(backup15);

        // when - insert at the beginning
        index.add(backup1);

        // then
        assertThat(index.all()).containsExactly(backup1, backup5, backup10, backup15);
      }
    }

    @Test
    void shouldHandleInsertionInMiddle()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup10 = createBackup(10, 1000L, 1100L);
      final var backup20 = createBackup(20, 2000L, 2100L);
      final var backup5 = createBackup(5, 500L, 600L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup10);
        index.add(backup20);

        // when - insert in the middle
        index.add(backup5);

        // then
        assertThat(index.all().map(IndexedBackup::checkpointId)).containsExactly(1L, 5L, 10L, 20L);
      }
    }

    @Test
    void shouldHandleInsertionAtEnd() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup5 = createBackup(5, 500L, 600L);
      final var backup10 = createBackup(10, 1000L, 1100L);
      final var backup20 = createBackup(20, 2000L, 2100L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup5);
        index.add(backup10);

        // when - insert at the end
        index.add(backup20);

        // then
        assertThat(index.all()).hasSize(4).element(3).isEqualTo(backup20);
      }
    }
  }

  @Nested
  class FindingBackups {

    @Test
    void shouldFindBackupByCheckpointId()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        final var backup1 = createBackup(1, 100L, 200L);
        final var backup2 = createBackup(2, 200L, 300L);
        final var backup3 = createBackup(3, 300L, 400L);
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // then
        assertThat(index.byCheckpointId(2L)).isEqualTo(backup2);
      }
    }

    @Test
    void shouldReturnNullWhenBackupNotFound()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup3 = createBackup(3, 300L, 400L);

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup3);

        // then
        assertThat(index.byCheckpointId(2L)).isNull();
      }
    }

    @Test
    void shouldReturnNullForEmptyIndex()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        // then
        assertThat(index.byCheckpointId(1L)).isNull();
      }
    }

    @Test
    void shouldHandleEdgeCaseWithSingleEntry()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup = createBackup(5, 500L, 600L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup);

        // when - searching for entries before and after
        final var foundExact = index.byCheckpointId(5L);
        final var foundBefore = index.byCheckpointId(4L);
        final var foundAfter = index.byCheckpointId(6L);

        // then
        assertThat(foundExact).extracting(IndexedBackup::checkpointId).isEqualTo(5L);
        assertThat(foundBefore).isNull();
        assertThat(foundAfter).isNull();
      }
    }
  }

  @Nested
  class RemovingBackups {

    @Test
    void shouldRemoveSingleEntry() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup = createBackup(1, 100L, 200L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup);
        assertThat(index.all()).hasSize(1);

        // when
        index.remove(backup.checkpointId());

        // then
        assertThat(index.all()).isEmpty();
        assertThat(index.byCheckpointId(1L)).isNull();
      }
    }

    @Test
    void shouldRemoveLastEntry() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // when
        index.remove(backup3.checkpointId());

        // then
        assertThat(index.all()).containsExactly(backup1, backup2);
        assertThat(index.byCheckpointId(3L)).isNull();
      }
    }

    @Test
    void shouldRemoveFirstEntry() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // when
        index.remove(backup1.checkpointId());

        // then
        assertThat(index.all()).containsExactly(backup2, backup3);
        assertThat(index.byCheckpointId(1L)).isNull();
      }
    }

    @Test
    void shouldRemoveMiddleEntry() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // when
        index.remove(backup2.checkpointId());

        // then
        assertThat(index.all()).containsExactly(backup1, backup3);
        assertThat(index.byCheckpointId(2L)).isNull();
      }
    }

    @Test
    void shouldHandleRemovalFromEmptyIndex()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup = createBackup(1, 100L, 200L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        // when - try to remove from empty index
        index.remove(backup.checkpointId());

        // then - should be no-op
        assertThat(index.all()).isEmpty();
      }
    }

    @Test
    void shouldHandleRemovalOfNonExistentEntry()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);
      final var nonExistent = createBackup(5, 500L, 600L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // when - try to remove non-existent entry
        index.remove(nonExistent.checkpointId());

        // then - should be no-op
        assertThat(index.all()).containsExactly(backup1, backup2, backup3);
      }
    }

    @Test
    void shouldRemoveMultipleEntries() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);
      final var backup4 = createBackup(4, 400L, 500L);
      final var backup5 = createBackup(5, 500L, 600L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);
        index.add(backup4);
        index.add(backup5);

        // when - remove multiple entries
        index.remove(backup2.checkpointId());
        index.remove(backup4.checkpointId());

        // then
        assertThat(index.all()).containsExactly(backup1, backup3, backup5);
        assertThat(index.byCheckpointId(2L)).isNull();
        assertThat(index.byCheckpointId(4L)).isNull();
      }
    }

    @Test
    void shouldRemoveAllEntries() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);

        // when - remove all entries
        index.remove(backup1.checkpointId());
        index.remove(backup2.checkpointId());
        index.remove(backup3.checkpointId());

        // then
        assertThat(index.all()).isEmpty();
      }
    }

    @Test
    void shouldRemoveEntryWithSameCheckpointIdButDifferentData()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);

        // when - try to remove with same checkpoint id but different data
        final var backup2Different = createBackup(2, 999L, 999L);
        index.remove(backup2Different.checkpointId());

        // then - should still remove based on checkpoint id
        assertThat(index.all()).containsExactly(backup1);
        assertThat(index.byCheckpointId(2L)).isNull();
      }
    }

    @Test
    void shouldHandleAddAfterRemoval() throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);
      final var backup4 = createBackup(4, 400L, 500L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup3);
        index.add(backup4);

        // when - remove middle entry and add it back
        index.remove(backup3.checkpointId());
        assertThat(index.all()).containsExactly(backup1, backup4);

        // Add backup2 in the gap
        index.add(backup2);

        // then
        assertThat(index.all()).containsExactly(backup1, backup2, backup4);
      }
    }
  }

  @Nested
  class PersistenceAndPerformance {

    @Test
    void shouldPersistDataAcrossReopens()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // Add initial backups
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
      }

      // Reopen and add more
      final var backup3 = createBackup(3, 300L, 400L);
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup3);
      }

      // Reopen and verify all backups are present
      try (final var index = CompactBackupIndex.open(indexFile)) {
        assertThat(index.all()).containsExactly(backup1, backup2, backup3);
      }
    }

    @Test
    void shouldPersistRemovalAcrossReopens()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      // Add backups
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);
      }

      // Reopen and remove backup2
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.remove(backup2.checkpointId());
      }

      // Reopen and verify backup2 is gone
      try (final var index = CompactBackupIndex.open(indexFile)) {
        assertThat(index.all()).containsExactly(backup1, backup3);
        assertThat(index.byCheckpointId(2L)).isNull();
      }
    }

    @Test
    void shouldHandleLargeNumberOfBackups()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var numBackups = 1000;

      // when
      try (final var index = CompactBackupIndex.open(indexFile)) {
        for (var i = numBackups; i > 0; i--) { // Insert in reverse order
          index.add(createBackup(i, (long) i * 100, (long) i * 200));
        }

        // then
        final var allBackups = index.all().collect(Collectors.toList());
        assertThat(allBackups).hasSize(numBackups);

        // Verify sorted order
        for (var i = 0; i < numBackups; i++) {
          assertThat(allBackups.get(i).checkpointId()).isEqualTo(i + 1);
        }

        // Verify binary search works
        for (long i = 1; i <= numBackups; i++) {
          assertThat(index.byCheckpointId(i)).extracting(IndexedBackup::checkpointId).isEqualTo(i);
        }
      }
    }

    @Test
    void shouldHandleLargeNumberOfRemovals()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var numBackups = 1000;

      try (final var index = CompactBackupIndex.open(indexFile)) {
        // Add many backups
        for (var i = 1; i <= numBackups; i++) {
          index.add(createBackup(i, (long) i * 100, (long) i * 200));
        }

        // when - remove every other backup
        for (var i = 2; i <= numBackups; i += 2) {
          // Data doesn't matter
          index.remove(createBackup(i, 0, 0).checkpointId());
        }

        // then - verify only odd-numbered backups remain
        try (final var entries = index.all()) {
          final var allBackups = entries.collect(Collectors.toList());
          assertThat(allBackups).hasSize(numBackups / 2);

          for (var i = 0; i < allBackups.size(); i++) {
            final var expectedCheckpointId = (long) (i * 2 + 1);
            assertThat(allBackups.get(i).checkpointId()).isEqualTo(expectedCheckpointId);
          }
        }

        // Verify removed entries are not found
        for (var i = 2; i <= numBackups; i += 2) {
          assertThat(index.byCheckpointId(i)).isNull();
        }
      }
    }

    @Test
    void shouldIterateThroughHugeIndex()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var numBackups = 10_000_000;

      try (final var index = CompactBackupIndex.open(indexFile)) {
        for (var i = 1; i <= numBackups; i++) {
          index.add(createBackup(i, (long) i * 100, (long) i * 200));
        }
        index.flush();

        // when
        try (final var entries = index.all()) {
          final var sum = entries.parallel().mapToLong(IndexedBackup::checkpointId).sum();

          // then
          final var expectedSum = (long) numBackups * (numBackups + 1) / 2; // Sum of 1..n
          assertThat(sum).isEqualTo(expectedSum);
        }
      }
    }

    @Test
    void iterationCanHandleConcurrentModification()
        throws IndexCorruption, IOException, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      try (final var index = CompactBackupIndex.open(indexFile)) {
        final var backup1 = createBackup(1, 100L, 200L);
        final var backup2 = createBackup(2, 200L, 300L);
        final var backup3 = createBackup(3, 300L, 400L);
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);
        final var openStream = index.all();

        // when - modify after opening the stream
        for (var i = 4; i <= 10_000; i++) {
          index.add(createBackup(i, i * 100L, (i + 1) * 100L));
        }

        // then - stream only shows entries as of time of opening
        assertThat(openStream).containsExactly(backup1, backup2, backup3);
      }
    }
  }

  // Helper methods

  @Nested
  class CorruptionHandling {

    @Test
    void shouldThrowIndexCorruptionForUnsupportedVersion()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(createBackup(1, 100L, 200L));
      }

      // Corrupt the version field
      try (final var channel = Files.newByteChannel(indexFile, StandardOpenOption.WRITE)) {
        final var buffer = ByteBuffer.allocate(4);
        buffer.putInt(999); // Invalid version
        buffer.flip();
        channel.write(buffer);
      }

      // when/then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(IndexCorruption.class)
          .hasMessageContaining("Unsupported backup index version: 999");
    }

    @Test
    void shouldThrowIllegalStateExceptionForNegativeEntryCount()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(createBackup(1, 100L, 200L));
      }

      // Corrupt the entry count field
      try (final var channel = Files.newByteChannel(indexFile, StandardOpenOption.WRITE)) {
        channel.position(4); // Skip version field
        final var buffer = ByteBuffer.allocate(4);
        buffer.putInt(-1); // Negative entry count
        buffer.flip();
        channel.write(buffer);
      }

      // when/then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Corrupt backup index: negative number of entries: -1");
    }

    @Test
    void shouldThrowIndexCorruptionForFileTooSmall()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(createBackup(1, 100L, 200L));
        index.add(createBackup(2, 200L, 300L));
      }

      // Corrupt by increasing entry count but not file size
      try (final var channel = Files.newByteChannel(indexFile, StandardOpenOption.WRITE)) {
        channel.position(4); // Skip version field
        final var buffer = ByteBuffer.allocate(4);
        buffer.putInt(5000); // Claim we have 5000 entries but file is too small
        buffer.flip();
        channel.write(buffer);
      }

      // when/then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(IndexCorruption.class)
          .hasMessageContaining(
              "Corrupt backup index: expected size for 5000 entries, but file is too small");
    }

    @Test
    void shouldThrowPartialIndexCorruptionForNonZeroBytesInRemainder()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
      }

      // Corrupt by writing non-zero byte in the remainder area (last few bytes)
      final var fileSize = Files.size(indexFile);
      try (final var channel =
          Files.newByteChannel(indexFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
        channel.position(fileSize - 1); // Last byte
        final var buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0xFF); // Non-zero byte
        buffer.flip();
        channel.write(buffer);
      }

      // when/then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(PartialIndexCorruption.class)
          .hasMessageContaining("Corrupt backup index: non-zero bytes found at")
          .hasMessageContaining("after last valid entry");
    }

    @Test
    void shouldDeleteTemporaryIndexFileOnOpen()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var tempFile = tempDir.resolve("backup.index.tmp");

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(createBackup(1, 100L, 200L));
      }

      // Create a temporary file
      Files.writeString(tempFile, "leftover temp file");
      assertThat(tempFile).exists();

      // when - open index again
      try (final var index = CompactBackupIndex.open(indexFile)) {
        // then - temporary file should be deleted
        assertThat(tempFile).doesNotExist();
        assertThat(index.all()).hasSize(1);
      }
    }

    @Test
    void shouldReturnLastValidEntryInPartialCorruption()
        throws IOException, IndexCorruption, PartialIndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");
      final var backup1 = createBackup(1, 100L, 200L);
      final var backup2 = createBackup(2, 200L, 300L);
      final var backup3 = createBackup(3, 300L, 400L);

      try (final var index = CompactBackupIndex.open(indexFile)) {
        index.add(backup1);
        index.add(backup2);
        index.add(backup3);
      }

      // Corrupt by writing non-zero byte after entries
      final long corruptPosition = 8 + (3 * 24) + 50;
      try (final var channel =
          Files.newByteChannel(indexFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
        channel.position(corruptPosition);
        final var buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0xff);
        buffer.flip();
        channel.write(buffer);
      }

      // then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(PartialIndexCorruption.class)
          .extracting(ex -> ((PartialIndexCorruption) ex).getLastValidEntry())
          .extracting(IndexedBackup::checkpointId)
          .isEqualTo(3L);
    }

    @Test
    void shouldHandlePartialCorruptionWithNoValidEntries() throws IOException, IndexCorruption {
      // given
      final var indexFile = tempDir.resolve("backup.index");

      // Create an index with header but claim we have entries and corrupt the data area
      try (final var channel =
          Files.newByteChannel(indexFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
        final var buffer = ByteBuffer.allocate(8 + 1024);
        buffer.putInt(1); // version
        buffer.putInt(0); // 0 entries
        // Fill with zeros initially
        while (buffer.hasRemaining()) {
          buffer.put((byte) 0);
        }
        buffer.flip();
        channel.write(buffer);
      }

      // Corrupt by writing non-zero byte in preallocated area
      try (final var channel =
          Files.newByteChannel(indexFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
        channel.position(100);
        final var buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0xAA);
        buffer.flip();
        channel.write(buffer);
      }

      // when/then
      assertThatThrownBy(() -> CompactBackupIndex.open(indexFile))
          .isInstanceOf(PartialIndexCorruption.class)
          .satisfies(
              ex -> {
                final var pic = (PartialIndexCorruption) ex;
                assertThat(pic.getLastValidEntry()).isNull();
                assertThat(pic.getMessage()).contains("no valid entries found");
              });
    }
  }
}
