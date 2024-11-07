/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class MappingStateTest {

  private MutableProcessingState processingState;
  private MutableMappingState mappingState;

  @BeforeEach
  public void setup() {
    mappingState = processingState.getMappingState();
  }

  @Test
  void shouldCreateMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);

    // when
    mappingState.create(mapping);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getMappingKey()).isEqualTo(key);
    assertThat(persistedMapping.getClaimName()).isEqualTo(claimName);
    assertThat(persistedMapping.getClaimValue()).isEqualTo(claimValue);
  }

  @Test
  void shouldReturnEmptyIfMappingDoesNotExist() {
    // when
    final var mapping = mappingState.get(1L);

    // then
    assertThat(mapping).isEmpty();
  }

  @Test
  void shouldRetrieveMappingByClaims() {
    // given
    final long key = 1L;
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    final var retrievedMapping = mappingState.get(claimName, claimValue);

    // then
    assertThat(retrievedMapping).isPresent();
    assertThat(retrievedMapping.get().getMappingKey()).isEqualTo(key);
  }

  @Test
  void shouldReturnEmptyIfMappingDoesNotExistByClaim() {
    // when
    final var mapping = mappingState.get("claimName", "claimValue");

    // then
    assertThat(mapping).isEmpty();
  }

  @Test
  void shouldAddRole() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);
    final long roleKey = 1L;

    // when
    mappingState.addRole(key, roleKey);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getRoleKeysList()).containsExactly(roleKey);
  }

  @Test
  void shouldRemoveRole() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);
    final long roleKey = 1L;
    mappingState.addRole(key, roleKey);

    // when
    mappingState.removeRole(key, roleKey);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getRoleKeysList()).isEmpty();
  }

  @Test
  void shouldAddTenant() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);
    final long tenantKey = 1L;

    // when
    mappingState.addTenant(key, tenantKey);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getTenantKeysList()).containsExactly(tenantKey);
  }

  @Test
  void shouldRemoveTenant() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);
    final long tenantKey = 1L;
    mappingState.addTenant(key, tenantKey);

    // when
    mappingState.removeTenant(key, tenantKey);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getTenantKeysList()).isEmpty();
  }

  @Test
  void shouldDeleteMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mapping =
        new MappingRecord().setMappingKey(key).setClaimName(claimName).setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    mappingState.delete(key);

    // then
    assertThat(mappingState.get(key)).isEmpty();
    assertThat(mappingState.get(claimName, claimValue)).isEmpty();
  }
}
