/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretStoreMetricsDoc.SecretStoreKeyNames;
import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

/**
 * The doc enum is the documentation for these meters: renaming one or a tag key silently breaks
 * every alert built on it, and there is no repo-wide check for that.
 */
final class SecretStoreMetricsDocTest {

  @Test
  void shouldDocumentSignInFailuresAsACounterTaggedByStoreAndStoreType() {
    // given
    final var meter = SecretStoreMetricsDoc.SIGNIN_FAILURES;

    // when/then
    assertThat(meter.getName()).isEqualTo("camunda.secret.store.signin.failures");
    assertThat(meter.getType()).isEqualTo(Type.COUNTER);
    assertThat(meter.getKeyNames())
        .containsExactly(SecretStoreKeyNames.STORE, SecretStoreKeyNames.STORE_TYPE);
    assertThat(SecretStoreKeyNames.STORE.asString()).isEqualTo("store");
    assertThat(SecretStoreKeyNames.STORE_TYPE.asString()).isEqualTo("storeType");
    assertThat(meter.getDescription()).isNotBlank();
  }

  @Test
  void shouldUseTheSameStoreTagKeyAsTheCacheMeters() {
    // when/then — a dashboard joins a store's sign-in failures with its cache meters on this key
    assertThat(SecretStoreKeyNames.STORE.asString())
        .isEqualTo(SecretCacheKeyNames.STORE.asString());
  }
}
