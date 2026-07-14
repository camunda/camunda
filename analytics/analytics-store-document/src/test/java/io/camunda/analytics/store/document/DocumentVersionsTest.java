/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The packing that makes the document stores' single-long external versioning enforce the
 * lexicographic {@code (epoch, offset)} fence: order preservation is the load-bearing property.
 */
final class DocumentVersionsTest {

  @Test
  void shouldPreserveTheLexicographicOrderOfWriteVersions() {
    // given versions in strictly increasing lexicographic order, spanning epoch and offset bumps
    final List<WriteVersion> ascending =
        List.of(
            WriteVersion.SEED,
            new WriteVersion(0, 1),
            new WriteVersion(0, (1L << 48) - 1),
            new WriteVersion(1, 0),
            new WriteVersion(1, 999),
            new WriteVersion(2, 5),
            new WriteVersion((1L << 15) - 1, (1L << 48) - 1));

    // when packed
    // then the packed longs are strictly increasing (and non-negative for the store)
    for (int i = 1; i < ascending.size(); i++) {
      final long previous = DocumentVersions.pack(ascending.get(i - 1));
      final long current = DocumentVersions.pack(ascending.get(i));
      assertThat(previous).isNotNegative().isLessThan(current);
    }
  }

  @Test
  void shouldPackEqualVersionsEqually() {
    // given the same version twice (a deterministic replay)
    // when / then the packed value is identical, so external_gte accepts the idempotent re-write
    assertThat(DocumentVersions.pack(new WriteVersion(5, 100)))
        .isEqualTo(DocumentVersions.pack(new WriteVersion(5, 100)));
  }

  @Test
  void shouldRejectVersionsOutsideThePackableBounds() {
    assertThatThrownBy(() -> DocumentVersions.pack(new WriteVersion(1L << 15, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("epoch");
    assertThatThrownBy(() -> DocumentVersions.pack(new WriteVersion(0, 1L << 48)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offset");
    assertThatThrownBy(() -> DocumentVersions.pack(new WriteVersion(-1, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentVersions.pack(new WriteVersion(0, -1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
