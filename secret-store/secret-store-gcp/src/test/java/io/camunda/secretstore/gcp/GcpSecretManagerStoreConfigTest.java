/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GcpSecretManagerStoreConfigTest {

  @Test
  void shouldRejectNullProjectId() {
    // when / then
    assertThatThrownBy(() -> new GcpSecretManagerStoreConfig(null, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("projectId");
  }

  @Test
  void shouldRejectBlankProjectId() {
    // when / then
    assertThatThrownBy(() -> new GcpSecretManagerStoreConfig(" ", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectId");
  }

  @Test
  void shouldRejectBlankContainerSecretId() {
    // when / then
    assertThatThrownBy(() -> new GcpSecretManagerStoreConfig("my-project", null, null, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("containerSecretId");
  }

  @Test
  void shouldDefaultOptionalFieldsInFactory() {
    // when
    final var config = GcpSecretManagerStoreConfig.of("my-project", "camunda-");

    // then
    assertThat(config.projectId()).isEqualTo("my-project");
    assertThat(config.pathPrefix()).isEqualTo("camunda-");
    assertThat(config.endpoint()).isNull();
    assertThat(config.containerSecretId()).isNull();
  }
}
