/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableSecretReference;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import org.junit.jupiter.api.Test;

/**
 * Pins the store ID the secret reference records default to against {@link
 * SecretStoreRegistry#DEFAULT_STORE_ID}.
 *
 * <p>The record layer stays free of the secret store API, so the three records repeat the
 * constant's value as a literal. This module sees both, so it is the only place the two can be
 * compared. Without this, renaming the constant would leave the records defaulting to a store the
 * registry no longer has — an exact lookup that misses every time, which is the failure this
 * default was introduced to remove (#59432).
 */
final class DefaultStoreIdTest {

  @Test
  void shouldDefaultEverySecretReferenceRecordToTheDefaultStore() {
    // given / when - records whose store ID was never set
    // then
    assertThat(new SecretReferenceRecord().getStoreId())
        .isEqualTo(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(new JobSecretReference().getStoreId())
        .isEqualTo(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(new ClusterVariableSecretReference().getStoreId())
        .isEqualTo(SecretStoreRegistry.DEFAULT_STORE_ID);
  }
}
