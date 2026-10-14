/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class StorageCapabilitiesTest {

  private static final StorageCapabilities ALL = new StorageCapabilities(true, true, true);
  private static final StorageCapabilities NONE = new StorageCapabilities(false, false, false);

  @Test
  void shouldAcceptConsumersWhenCapabilitiesAreProvided() {
    // when - then
    assertThatCode(
            () -> {
              ALL.requireFileSnapshots("consumer");
              ALL.requireEphemeralRuntime("consumer");
              ALL.requireScopedCopy("consumer");
            })
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNameConsumerAndCapabilityWhenFileSnapshotsAreMissing() {
    // when - then
    assertThatThrownBy(() -> NONE.requireFileSnapshots("StateControllerImpl"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("StateControllerImpl")
        .hasMessageContaining("fileSnapshots");
  }
}
