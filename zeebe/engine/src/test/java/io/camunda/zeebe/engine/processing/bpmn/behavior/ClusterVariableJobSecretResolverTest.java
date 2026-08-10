/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.state.clustervariable.ClusterVariableInstance;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ClusterVariableJobSecretResolverTest {

  private final ClusterVariableState clusterVariableState = mock(ClusterVariableState.class);
  private final ClusterVariableJobSecretResolver resolver =
      new ClusterVariableJobSecretResolver(clusterVariableState);

  private static ClusterVariableInstance instanceWith(final ClusterVariableRecord record) {
    final var instance = new ClusterVariableInstance();
    instance.setRecord(record);
    return instance;
  }

  @Test
  void shouldFoldWholeVariableSecretReference() {
    // given
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "/token");
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references =
        Map.of("/tokens/t", Set.of(new ClusterVariableReference("tenant", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result)
        .containsExactlyEntriesOf(
            Map.of("/tokens/t/token", Set.of(new SecretReference("", "token"))));
  }

  @Test
  void shouldRebaseSecretUnderAccessedFieldPath() {
    // given: variable value has the secret at /a/b/token, mapping accesses .a.b of the variable
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "/a/b/token");
    when(clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("myVar")))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references =
        Map.of(
            "/auth", Set.of(new ClusterVariableReference("cluster", "myVar", List.of("a", "b"))));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then: the rebased pointer is only /token (the a/b prefix is consumed), appended to /auth
    assertThat(result)
        .containsExactlyEntriesOf(Map.of("/auth/token", Set.of(new SecretReference("", "token"))));
  }

  @Test
  void shouldExcludeSecretOutsideAccessedFieldPath() {
    // given: the secret lives under /other, but the mapping only accesses .a of the variable
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "/other/token");
    when(clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("myVar")))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references =
        Map.of("/auth", Set.of(new ClusterVariableReference("cluster", "myVar", List.of("a"))));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then: nothing folds — the accessed sub-value never contains this secret
    assertThat(result).isEmpty();
  }

  @Test
  void shouldTreatSiblingSegmentNameAsNonMatchNotPrefixMatch() {
    // given: guards against naive string-prefix rebasing (/a would wrongly prefix-match /ab/token)
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "/ab/token");
    when(clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("myVar")))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references =
        Map.of("/auth", Set.of(new ClusterVariableReference("cluster", "myVar", List.of("a"))));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNoOpForJsonKindVariable() {
    // given
    final var record =
        new ClusterVariableRecord().setName("myVar").setKind(ClusterVariableKind.JSON);
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references =
        Map.of("/tokens/t", Set.of(new ClusterVariableReference("tenant", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNoOpForMissingVariable() {
    // given
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.empty());

    final var references =
        Map.of("/tokens/t", Set.of(new ClusterVariableReference("tenant", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldFallBackFromTenantToGlobalScopeForEnvReference() {
    // given: no tenant-scoped variable, only a global one — env must still resolve it
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "/token");
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.empty());
    when(clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("myVar")))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references = Map.of("/auth", Set.of(new ClusterVariableReference("env", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result)
        .containsExactlyEntriesOf(Map.of("/auth/token", Set.of(new SecretReference("", "token"))));
  }

  @Test
  void shouldPreferTenantScopeOverGlobalForEnvReference() {
    // given: both a tenant-scoped and a global variable exist under the same name
    final var tenantRecord =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "tenant-token", "/token");
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.of(instanceWith(tenantRecord)));

    final var references = Map.of("/auth", Set.of(new ClusterVariableReference("env", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then: tenant wins, and global is never even consulted
    assertThat(result)
        .containsExactlyEntriesOf(
            Map.of("/auth/token", Set.of(new SecretReference("", "tenant-token"))));
    verify(clusterVariableState, never()).getGloballyScopedClusterVariable(any());
  }

  @Test
  void shouldUseEmptyPointerWhenSecretIsTheWholeMappedValue() {
    // given: the variable's whole value (or the whole accessed field) is a bare secret string
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setKind(ClusterVariableKind.SECRET_REFERENCE)
            .addSecretReference("", "token", "");
    when(clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("myVar"), "tenant-1"))
        .thenReturn(Optional.of(instanceWith(record)));

    final var references = Map.of("/auth", Set.of(new ClusterVariableReference("tenant", "myVar")));

    // when
    final var result = resolver.resolve(references, "tenant-1");

    // then
    assertThat(result)
        .containsExactlyEntriesOf(Map.of("/auth", Set.of(new SecretReference("", "token"))));
  }
}
