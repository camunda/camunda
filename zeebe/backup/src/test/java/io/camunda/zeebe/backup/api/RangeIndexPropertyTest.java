/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static io.camunda.zeebe.backup.api.Util.descriptor;
import static io.camunda.zeebe.backup.api.Util.id;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.backup.api.RangeIndex.Range;
import io.camunda.zeebe.backup.api.RangeIndexPropertyTest.Operation.AddOperation;
import io.camunda.zeebe.backup.api.RangeIndexPropertyTest.Operation.RemoveOperation;
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

public class RangeIndexPropertyTest {

  /**
   * Verifies that after a series of add and remove operations, the ascending and descending indexes
   * are consistent with each other. This does not only catch simple mistakes where we forget to add
   * or remove from one of the indexes, but also subtle behavior bugs because the sets drop entries
   * where the comparator returns 0 and both indexes use different comparators.
   */
  @Property(tries = 2000, shrinking = ShrinkingMode.FULL)
  void shouldMaintainIdenticalIndexes(@ForAll("rangeActions") final ActionChain<RangeIndex> chain) {
    chain
        .withInvariant(
            rangeIndex ->
                assertThat(rangeIndex.ascendingRanges())
                    .containsExactlyElementsOf(rangeIndex.descendingRanges().reversed()))
        .withInvariant(index -> {})
        .run();
  }

  @Provide
  Arbitrary<ActionChain<RangeIndex>> rangeActions() {
    return ActionChain.startWith(RangeIndex::new)
        .withMaxTransformations(100)
        // Generate more add actions than remove actions to avoid empty indexes
        .withAction(2, new AddAction())
        .withAction(1, new RemoveAction());
  }

  @Provide
  Arbitrary<Operation> addOperations() {
    return Arbitraries.longs()
        .greaterOrEqual(1)
        .withoutEdgeCases()
        .flatMap(
            backupId ->
                Arbitraries.longs()
                    .between(backupId - 1, 1000)
                    .lessOrEqual(backupId - 1)
                    .injectNull(0.3)
                    .withoutEdgeCases()
                    .flatMap(
                        previousBackupId ->
                            Arbitraries.longs()
                                .between(backupId + 1, 1000)
                                .injectNull(0.3)
                                .withoutEdgeCases()
                                .map(
                                    nextBackupId ->
                                        new AddOperation(
                                            backupId, previousBackupId, nextBackupId))));
  }

  @Provide
  Arbitrary<Operation> removeOperations() {
    return Arbitraries.longs()
        .greaterOrEqual(1)
        .withoutEdgeCases()
        .flatMap(
            backupId ->
                Arbitraries.longs()
                    .between(backupId - 1, 1000)
                    .withoutEdgeCases()
                    .flatMap(
                        previousBackupId ->
                            Arbitraries.longs()
                                .between(backupId + 1, 1000)
                                .withoutEdgeCases()
                                .map(
                                    nextBackupId ->
                                        new RemoveOperation(
                                            backupId, previousBackupId, nextBackupId))));
  }

  static Arbitrary<Tuple3<Long, Long, Long>> arbitraryBackup() {
    return Arbitraries.longs()
        .greaterOrEqual(1)
        .withoutEdgeCases()
        .flatMap(
            backupId ->
                Arbitraries.longs()
                    .lessOrEqual(backupId - 1)
                    .withoutEdgeCases()
                    .flatMap(
                        previousBackupId ->
                            Arbitraries.longs()
                                .greaterOrEqual(backupId + 1)
                                .withoutEdgeCases()
                                .map(
                                    nextBackupId ->
                                        Tuple.of(backupId, previousBackupId, nextBackupId))));
  }

  static final class RemoveAction implements Action.Dependent<RangeIndex> {

    @Override
    public Arbitrary<Transformer<RangeIndex>> transformer(final RangeIndex state) {
      return arbitraryBackup()
          .map(
              backup -> {
                final var backupId = backup.get1();
                final var previousBackupId = backup.get2();
                final var nextBackupId = backup.get3();
                final var existingRange = state.lookup(id(nextBackupId));
                // Only generate a range that is consistent with the current state.
                // This upholds the API contract of `RangeIndex#remove`.
                final var minPreviousId =
                    existingRange != null
                            && existingRange.firstBackup().checkpointId() > previousBackupId
                        ? existingRange.firstBackup()
                        : id(previousBackupId);
                final var maxNextId =
                    existingRange != null
                            && existingRange.lastBackup().checkpointId() < nextBackupId
                        ? existingRange.lastBackup()
                        : id(nextBackupId);
                return Transformer.transform(
                    "Remove %s[%s,%s]".formatted(backupId, minPreviousId, maxNextId),
                    mutableState -> {
                      state.remove(id(backupId), descriptor(minPreviousId, maxNextId));
                      return state;
                    });
              });
    }
  }

  static final class AddAction implements Action.Dependent<RangeIndex> {
    @Override
    public Arbitrary<Transformer<RangeIndex>> transformer(final RangeIndex state) {
      return arbitraryBackup()
          .map(
              backup -> {
                final var backupId = backup.get1();
                final var previousBackupId = backup.get2();
                final var nextBackupId = backup.get3();
                final var existingRange = state.lookup(id(nextBackupId));
                if (Range.adjacent(existingRange, new Range(id(previousBackupId), id(nextBackupId)))
                    || Range.adjacent(
                        new Range(id(previousBackupId), id(nextBackupId)), existingRange)) {
                  return Transformer.transform(
                      "Add %s[%s,%s]".formatted(backupId, previousBackupId, nextBackupId),
                      mutableState -> {
                        state.remove(
                            id(backupId), descriptor(id(previousBackupId), id(nextBackupId)));
                        return state;
                      });
                } else {
                  // Skip this addition because it is inconsistent with the current state.
                  // This upholds the API contract of `RangeIndex#add`.
                  return Transformer.noop();
                }
              });
    }
  }

  sealed interface Operation {
    record AddOperation(long backupId, Long previousId, Long nextId) implements Operation {}

    record RemoveOperation(long backupId, Long previousId, Long nextId) implements Operation {}
  }
}
