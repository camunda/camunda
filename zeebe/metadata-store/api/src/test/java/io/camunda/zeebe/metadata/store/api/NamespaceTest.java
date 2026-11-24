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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Namespace names follow their own rule, wider than keys: they allow uppercase and '_'. */
final class NamespaceTest {

  @ParameterizedTest
  @ValueSource(strings = {"config", "region-failover", "tenant01", "tenant-01"})
  void shouldAcceptValidName(final String name) {
    // when - then
    assertThatCode(() -> Namespace.cluster(name)).doesNotThrowAnyException();
    assertThatCode(() -> Namespace.tenant(name)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a.b", "a b", "a/b", "a:b", "ä", "a\u0000b", "Tenant", "tenant_a"})
  void shouldRejectInvalidName(final String name) {
    // when - then
    assertThatThrownBy(() -> Namespace.cluster(name)).isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> Namespace.tenant(name)).isInstanceOf(InvalidArgumentException.class);
  }
}
