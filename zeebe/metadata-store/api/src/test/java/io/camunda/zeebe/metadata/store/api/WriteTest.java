/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.Write.Check;
import io.camunda.zeebe.metadata.store.api.Write.CreateNamespace;
import io.camunda.zeebe.metadata.store.api.Write.Delete;
import io.camunda.zeebe.metadata.store.api.Write.DeletePrefix;
import io.camunda.zeebe.metadata.store.api.Write.Put;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The rules of a write and of a batch that need no store state. */
final class WriteTest {

  private static final Namespace NAMESPACE = Namespace.cluster("config");
  private static final Namespace OTHER_NAMESPACE = Namespace.cluster("other");
  private static final StoreKey EMPTY = StoreKey.of(NAMESPACE, "");
  private static final byte[] VALUE = {1};

  @ParameterizedTest(name = "{0}")
  @MethodSource("keyWrites")
  void shouldRejectEmptyKey(final String kind, final Function<StoreKey, Write> write) {
    // when - then
    assertThatThrownBy(() -> write.apply(EMPTY)).isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldAcceptValidBatch() {
    // given
    final var tenant = Namespace.tenant("tenant-a");
    final var batch =
        List.<Write>of(
            new CreateNamespace(tenant),
            new Put(StoreKey.of(tenant, "camunda.a"), VALUE),
            new DeletePrefix(key("a.b")),
            new Put(key("a.bc"), VALUE),
            new Delete(key("a")),
            new Check(key("c"), Condition.absent()),
            new Put(StoreKey.of(OTHER_NAMESPACE, "a.b.c"), VALUE));

    // when - then
    assertThatCode(() -> Write.validateBatch(batch)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectSameKeyTwice() {
    // given
    final var batch = List.<Write>of(new Put(key("a"), VALUE), new Delete(key("a")));

    // when - then
    assertThatThrownBy(() -> Write.validateBatch(batch))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldRejectCheckOnWrittenKey() {
    // given
    final var batch =
        List.<Write>of(new Check(key("a"), Condition.exists()), new Put(key("a"), VALUE));

    // when - then
    assertThatThrownBy(() -> Write.validateBatch(batch))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldRejectSamePrefixDeletedTwice() {
    // given
    final var batch = List.<Write>of(new DeletePrefix(key("a")), new DeletePrefix(key("a")));

    // when - then
    assertThatThrownBy(() -> Write.validateBatch(batch))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @ParameterizedTest(name = "{0} at {1}")
  @MethodSource("writesAtOrUnderPrefix")
  void shouldRejectWriteAtOrUnderDeletedPrefix(
      final String kind, final String key, final Function<StoreKey, Write> write) {
    // given
    final var deletePrefix = new DeletePrefix(key("a.b"));
    final var other = write.apply(key(key));

    // when - then
    assertThatThrownBy(() -> Write.validateBatch(List.of(deletePrefix, other)))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> Write.validateBatch(List.of(other, deletePrefix)))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldRejectNamespaceCreatedTwice() {
    // given
    final var batch =
        List.<Write>of(
            new CreateNamespace(Namespace.tenant("tenant-a")),
            new CreateNamespace(Namespace.tenant("tenant-a")));

    // when - then
    assertThatThrownBy(() -> Write.validateBatch(batch))
        .isInstanceOf(InvalidArgumentException.class);
  }

  static Stream<Arguments> keyWrites() {
    return Stream.of(
        Arguments.of("Put", (Function<StoreKey, Write>) key -> new Put(key, VALUE)),
        Arguments.of("Delete", (Function<StoreKey, Write>) Delete::new),
        Arguments.of("DeletePrefix", (Function<StoreKey, Write>) DeletePrefix::new),
        Arguments.of(
            "Check", (Function<StoreKey, Write>) key -> new Check(key, Condition.exists())));
  }

  static Stream<Arguments> writesAtOrUnderPrefix() {
    return keyWrites()
        .filter(arguments -> !"DeletePrefix".equals(arguments.get()[0]))
        .flatMap(
            arguments ->
                Stream.of("a.b", "a.b.c")
                    .map(key -> Arguments.of(arguments.get()[0], key, arguments.get()[1])));
  }

  private static StoreKey key(final String key) {
    return StoreKey.of(NAMESPACE, key);
  }
}
