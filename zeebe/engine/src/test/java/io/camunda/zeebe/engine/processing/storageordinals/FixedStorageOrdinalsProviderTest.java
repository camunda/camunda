/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FixedStorageOrdinalsProviderTest {

  @Test
  void shouldRoundRobinStorageOrdinals() {
    // given
    final var provider = new FixedStorageOrdinalsProvider(List.of(1, 2, 3, 4));

    // when/then
    assertThat(provider.getStorageOrdinal()).isEqualTo(1);
    assertThat(provider.getStorageOrdinal()).isEqualTo(2);
    assertThat(provider.getStorageOrdinal()).isEqualTo(3);
    assertThat(provider.getStorageOrdinal()).isEqualTo(4);
    assertThat(provider.getStorageOrdinal()).isEqualTo(1);
    assertThat(provider.getStorageOrdinal()).isEqualTo(2);
    assertThat(provider.getStorageOrdinal()).isEqualTo(3);
  }
}
