/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The store accepts keys in one canonical form only, and matches prefixes on whole segments. */
final class StoreKeyTest {

  private static final Namespace NAMESPACE = Namespace.cluster("config");

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "camunda.data.primary-storage.backup.s3", "region-1.broker-0"})
  void shouldAcceptCanonicalKey(final String key) {
    // when - then
    assertThatCode(() -> StoreKey.of(NAMESPACE, key)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "bucketName",
        "BUCKET",
        "bucket_name",
        "a..b",
        ".a",
        "a.",
        ".",
        "a b",
        "a\u0000b",
        "ä",
        "a/b"
      })
  void shouldRejectKeyNotInCanonicalForm(final String key) {
    // when - then
    assertThatThrownBy(() -> StoreKey.of(NAMESPACE, key))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldMatchPrefixOnWholeSegments() {
    // given
    final var prefix = StoreKey.of(NAMESPACE, "a.b");

    // when - then
    assertThat(StoreKey.of(NAMESPACE, "a.b").isAtOrUnder(prefix)).isTrue();
    assertThat(StoreKey.of(NAMESPACE, "a.b.c").isAtOrUnder(prefix)).isTrue();
    assertThat(StoreKey.of(NAMESPACE, "a.bc").isAtOrUnder(prefix)).isFalse();
    assertThat(StoreKey.of(NAMESPACE, "a").isAtOrUnder(prefix)).isFalse();
  }

  @Test
  void shouldTreatEmptyKeyAsPrefixOfItsNamespaceOnly() {
    // given
    final var root = StoreKey.of(NAMESPACE, "");

    // when - then
    assertThat(StoreKey.of(NAMESPACE, "a.b").isAtOrUnder(root)).isTrue();
    assertThat(StoreKey.of(Namespace.cluster("other"), "a.b").isAtOrUnder(root)).isFalse();
  }
}
