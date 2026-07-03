/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SecretRefTest {

  @Test
  void shouldCreateWithName() {
    // given / when
    final var ref = new SecretRef("my-secret");

    // then
    assertThat(ref.name()).isEqualTo("my-secret");
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> new SecretRef(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> new SecretRef("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldRejectEmptyName() {
    assertThatThrownBy(() -> new SecretRef(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldSupportEqualityAndHashCode() {
    // given
    final var ref1 = new SecretRef("my-secret");
    final var ref2 = new SecretRef("my-secret");

    // then
    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
  }

  @Test
  void shouldBeUsableAsMapKey() {
    // given
    final var map = new HashMap<SecretRef, String>();
    final var ref = new SecretRef("my-secret");
    map.put(ref, "value");

    // when / then
    assertThat(map.get(new SecretRef("my-secret"))).isEqualTo("value");
  }
}
