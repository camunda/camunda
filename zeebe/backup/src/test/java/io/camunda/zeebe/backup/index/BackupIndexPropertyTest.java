/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.backup.index;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.BackupIndex.IndexedBackup;
import io.camunda.zeebe.backup.index.CompactBackupIndex.IndexCorruption;
import io.camunda.zeebe.backup.index.CompactBackupIndex.PartialIndexCorruption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple3;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

final class BackupIndexPropertyTest {

  /**
   * Verifies that an in-memory sorted set and the compact backup index maintain identical indexes
   * throughout a series of add and remove operations.
   */
  @Property(tries = 10, shrinking = ShrinkingMode.FULL)
  void shouldMaintainIdenticalIndexes(@ForAll("indexActions") final ActionChain<TestModel> chain) {
    final var resultingModel =
        chain
            .withInvariant(
                model -> {
                  final var inMemoryBackups = model.getInMemorySet().toList();
                  final var indexBackups = model.getCompactBackupIndex().toList();

                  assertThat(indexBackups)
                      .as("The compact backup index should match the in-memory set")
                      .containsExactlyElementsOf(inMemoryBackups);
                })
            .run();

    resultingModel.close();
  }

  @Provide
  Arbitrary<ActionChain<TestModel>> indexActions() {
    return ActionChain.startWith(TestModel::new)
        // Generate many transformations to exercise remapping of the indexes
        .withMaxTransformations(2048)
        // Generate more add actions than remove actions to avoid empty indexes
        .withAction(50, new AddAction())
        .withAction(5, new RemoveAction())
        // Occasionally reopen the index to verify persistence
        .withAction(1, new ReopenAction());
  }

  static Arbitrary<Tuple3<Long, Long, Long>> arbitraryEntry() {
    return Arbitraries.longs()
        .greaterOrEqual(1)
        .withoutEdgeCases()
        .flatMap(
            backupId ->
                Arbitraries.longs()
                    .lessOrEqual(backupId - 1)
                    .withoutEdgeCases()
                    .flatMap(
                        firstLogPosition ->
                            Arbitraries.longs()
                                .greaterOrEqual(backupId + 1)
                                .withoutEdgeCases()
                                .map(
                                    lastLogPosition ->
                                        Tuple.of(backupId, firstLogPosition, lastLogPosition))));
  }

  static final class RemoveAction implements Action.Dependent<TestModel> {

    @Override
    public Arbitrary<Transformer<TestModel>> transformer(final TestModel state) {
      return arbitraryEntry()
          .map(
              backup -> {
                final var checkpointId = backup.get1();
                final var firstLogIndex = backup.get2();
                final var lastLogIndex = backup.get3();
                return Transformer.transform(
                    "Remove %s[%s,%s]".formatted(checkpointId, firstLogIndex, lastLogIndex),
                    mutableState -> {
                      state.removeBackup(
                          new IndexedBackup(checkpointId, firstLogIndex, lastLogIndex));
                      return state;
                    });
              });
    }
  }

  static final class AddAction implements Action.Dependent<TestModel> {
    @Override
    public Arbitrary<Transformer<TestModel>> transformer(final TestModel state) {
      return arbitraryEntry()
          .map(
              backup -> {
                final var checkpointId = backup.get1();
                final var firstLogIndex = backup.get2();
                final var lastLogIndex = backup.get3();
                return Transformer.transform(
                    "Add %s[%s,%s]".formatted(checkpointId, firstLogIndex, lastLogIndex),
                    mutableState -> {
                      state.addBackup(new IndexedBackup(checkpointId, firstLogIndex, lastLogIndex));
                      return state;
                    });
              });
    }
  }

  static final class ReopenAction implements Action.Dependent<TestModel> {
    @Override
    public Arbitrary<Transformer<TestModel>> transformer(final TestModel state) {
      return Arbitraries.just(
          Transformer.transform(
              "Reopen Index",
              mutableState -> {
                try {
                  mutableState.compactBackupIndex.close();
                  mutableState.compactBackupIndex = CompactBackupIndex.open(mutableState.indexPath);
                } catch (final IOException | IndexCorruption | PartialIndexCorruption e) {
                  throw new RuntimeException(e);
                }
                return mutableState;
              }));
    }
  }

  static final class TestModel implements AutoCloseable {
    Path indexPath;
    SortedSet<IndexedBackup> inMemorySet =
        new TreeSet<>(Comparator.comparing(IndexedBackup::checkpointId));
    CompactBackupIndex compactBackupIndex;

    TestModel() {
      try {
        indexPath = Files.createTempFile("backup", ".index");
        compactBackupIndex = CompactBackupIndex.open(indexPath);
      } catch (final IOException | IndexCorruption | PartialIndexCorruption e) {
        throw new RuntimeException(e);
      }
    }

    void addBackup(final IndexedBackup backup) {
      inMemorySet.add(backup);
      compactBackupIndex.add(backup);
    }

    void removeBackup(final IndexedBackup backup) {
      inMemorySet.remove(backup);
      compactBackupIndex.remove(backup);
    }

    Stream<IndexedBackup> getInMemorySet() {
      return inMemorySet.stream();
    }

    Stream<IndexedBackup> getCompactBackupIndex() {
      return compactBackupIndex.all();
    }

    @Override
    public void close() {
      try {
        compactBackupIndex.close();
        Files.delete(indexPath);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
