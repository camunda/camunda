/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.Batch;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException.FailedCondition;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceAlreadyExistsException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.Condition;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.metadata.store.impl.state.Entry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import io.camunda.zeebe.metadata.store.impl.state.EntryApplier;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The single writer's decision: only changes reach an entry, a failed condition rejects the whole
 * batch, and evaluation never changes the state it is given.
 */
final class WriteEvaluatorTest {

  private static final Namespace CONFIG = Namespace.cluster("config");
  private static final Namespace TENANT = Namespace.tenant("tenant-a");
  private static final StoreValue V1 = StoreValue.of(new byte[] {1});
  private static final StoreValue V2 = StoreValue.of(new byte[] {2});

  /** CONFIG holds a = V1, a.b = V1 and a.b.c = V1; TENANT does not exist. */
  private final StoreState state =
      apply(
          StoreState.empty(),
          new NamespaceEntry.Created(
              CONFIG,
              List.of(
                  new Operation.Put("a", V1),
                  new Operation.Put("a.b", V1),
                  new Operation.Put("a.b.c", V1))));

  @ParameterizedTest(name = "{0} on {1}")
  @MethodSource("conditions")
  void shouldEvaluateCondition(
      final String condition, final String key, final Condition value, final boolean holds) {
    // given
    final var write = new Write.Put(key(key), V2, value);

    // when - then
    if (holds) {
      assertThat(WriteEvaluator.evaluate(state, Batch.of(write)))
          .containsExactly(changed(CONFIG, new Operation.Put(key, V2)));
    } else {
      assertThatThrownBy(() -> WriteEvaluator.evaluate(state, Batch.of(write)))
          .isInstanceOf(ConditionFailedException.class);
    }
  }

  static Stream<Arguments> conditions() {
    return Stream.of(
        Arguments.of("absent", "x", Condition.absent(), true),
        Arguments.of("absent", "a", Condition.absent(), false),
        Arguments.of("exists", "a", Condition.exists(), true),
        Arguments.of("exists", "x", Condition.exists(), false),
        Arguments.of(
            "valueEquals", "a", Condition.valueEquals(StoreValue.of(new byte[] {1})), true),
        Arguments.of("valueEquals", "a", Condition.valueEquals(V2), false),
        Arguments.of("valueEquals", "x", Condition.valueEquals(V1), false));
  }

  @Test
  void shouldReportEachFailedKeyWithItsCurrentValue() {
    // given
    final var batch =
        Batch.of(
            new Write.Check(key("a"), Condition.absent()),
            new Write.Put(key("x"), V2, Condition.exists()),
            new Write.Put(key("y"), V2));

    // when - then
    assertThatThrownBy(() -> WriteEvaluator.evaluate(state, batch))
        .isInstanceOfSatisfying(
            ConditionFailedException.class,
            e ->
                assertThat(e.failed())
                    .containsExactly(
                        new FailedCondition(key("a"), new KeyValue(key("a"), V1, null)),
                        new FailedCondition(key("x"), null)));
  }

  @Test
  void shouldSeeNamespaceCreatedEarlierInSameBatch() {
    // given - the check fails on the empty new namespace instead of on an unknown one
    final var batch =
        Batch.of(
            new Write.CreateNamespace(TENANT),
            new Write.Check(StoreKey.of(TENANT, "a"), Condition.exists()));

    // when - then
    assertThatThrownBy(() -> WriteEvaluator.evaluate(state, batch))
        .isInstanceOfSatisfying(
            ConditionFailedException.class,
            e ->
                assertThat(e.failed())
                    .extracting(FailedCondition::key)
                    .containsExactly(StoreKey.of(TENANT, "a")));
  }

  @Test
  void shouldCreateNamespaceAndWriteIntoItAcrossNamespaces() {
    // given
    final var batch =
        Batch.of(
            new Write.CreateNamespace(TENANT),
            new Write.Put(StoreKey.of(TENANT, "camunda.a"), V1),
            new Write.Put(key("a"), V2));

    // when
    final var entry = WriteEvaluator.evaluate(state, batch);

    // then
    assertThat(entry)
        .containsExactly(
            changed(CONFIG, new Operation.Put("a", V2)),
            new NamespaceEntry.Created(TENANT, List.of(new Operation.Put("camunda.a", V1))));
  }

  @Test
  void shouldResolveDeletePrefixToDeletesOfKeysItRemoves() {
    // when
    final var entry = WriteEvaluator.evaluate(state, Batch.of(new Write.DeletePrefix(key("a.b"))));

    // then
    assertThat(entry)
        .containsExactly(
            changed(CONFIG, new Operation.Delete("a.b"), new Operation.Delete("a.b.c")));
  }

  @Test
  void shouldWriteNothingWhenNothingChanges() {
    // given
    final var batch =
        Batch.of(
            new Write.Put(key("a"), StoreValue.of(new byte[] {1})),
            new Write.Delete(key("missing")),
            new Write.DeletePrefix(key("x")),
            new Write.Check(key("a.b"), Condition.exists()));

    // when - then
    assertThat(WriteEvaluator.evaluate(state, batch)).isEmpty();
  }

  @Test
  void shouldCreateEmptyNamespace() {
    // when - then
    assertThat(WriteEvaluator.evaluate(state, Batch.of(new Write.CreateNamespace(TENANT))))
        .containsExactly(new NamespaceEntry.Created(TENANT, List.of()));
  }

  @Test
  void shouldRejectWriteToUnknownNamespace() {
    // given
    final var batch = Batch.of(new Write.Put(StoreKey.of(TENANT, "a"), V1));

    // when - then
    assertThatThrownBy(() -> WriteEvaluator.evaluate(state, batch))
        .isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldRejectCreatingExistingNamespace() {
    // when - then
    assertThatThrownBy(
            () -> WriteEvaluator.evaluate(state, Batch.of(new Write.CreateNamespace(CONFIG))))
        .isInstanceOf(NamespaceAlreadyExistsException.class);
  }

  @Test
  void shouldDeleteNamespace() {
    // when - then
    assertThat(WriteEvaluator.evaluateDeleteNamespace(state, CONFIG))
        .isEqualTo(new NamespaceEntry.Deleted(CONFIG));
    assertThatThrownBy(() -> WriteEvaluator.evaluateDeleteNamespace(state, TENANT))
        .isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldNotChangeGivenState() {
    // given
    final var before = state.requireNamespace(CONFIG);

    // when
    WriteEvaluator.evaluate(
        state, Batch.of(new Write.Put(key("a"), V2), new Write.Delete(key("a.b"))));

    // then
    assertThat(state.requireNamespace(CONFIG)).isSameAs(before);
    assertThat(state.get(key("a.b"))).isPresent();
  }

  private static StoreState apply(final StoreState state, final NamespaceEntry... namespaces) {
    final var position = state.appliedPosition() + 1;
    return EntryApplier.apply(state, position, new Entry("8.11.0", 0L, List.of(namespaces)))
        .state();
  }

  private static NamespaceEntry changed(final Namespace namespace, final Operation... operations) {
    return new NamespaceEntry.KeysChanged(namespace, List.of(operations));
  }

  private static StoreKey key(final String key) {
    return StoreKey.of(CONFIG, key);
  }
}
