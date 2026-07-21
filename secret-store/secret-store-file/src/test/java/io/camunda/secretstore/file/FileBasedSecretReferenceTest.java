/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class FileBasedSecretReferenceTest {

  @Test
  void shouldCreateWithName() {
    // given / when
    final var ref = new FileBasedSecretReference("my-secret");

    // then
    assertThat(ref.name()).isEqualTo("my-secret");
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> new FileBasedSecretReference(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> new FileBasedSecretReference("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldRejectEmptyName() {
    assertThatThrownBy(() -> new FileBasedSecretReference(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldRejectNameWithForwardSlash() {
    assertThatThrownBy(() -> new FileBasedSecretReference("nested/secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path separator");
  }

  @Test
  void shouldRejectNameWithBackslash() {
    assertThatThrownBy(() -> new FileBasedSecretReference("nested\\secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path separator");
  }

  @Test
  void shouldRejectCurrentDirectoryToken() {
    assertThatThrownBy(() -> new FileBasedSecretReference("."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traversal");
  }

  @Test
  void shouldRejectParentDirectoryToken() {
    assertThatThrownBy(() -> new FileBasedSecretReference(".."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traversal");
  }

  @Test
  void shouldSupportEqualityAndHashCode() {
    // given
    final var ref1 = new FileBasedSecretReference("my-secret");
    final var ref2 = new FileBasedSecretReference("my-secret");

    // then
    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
  }

  @Test
  void shouldBeUsableAsMapKey() {
    // given
    final var map = new HashMap<FileBasedSecretReference, String>();
    final var ref = new FileBasedSecretReference("my-secret");
    map.put(ref, "value");

    // when / then
    assertThat(map.get(new FileBasedSecretReference("my-secret"))).isEqualTo("value");
  }
}
