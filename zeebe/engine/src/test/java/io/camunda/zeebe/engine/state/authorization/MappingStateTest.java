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
    final String name = "name";
    final String id = "id";
    final var mapping =
        new MappingRecord()
            .setMappingKey(key)
            .setId(id)
            .setClaimName(claimName)
            .setName(name)
            .setClaimValue(claimValue);

    // when
    mappingState.create(mapping);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getMappingKey()).isEqualTo(key);
    assertThat(persistedMapping.getId()).isEqualTo(id);
    assertThat(persistedMapping.getName()).isEqualTo(name);
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
    final String id = "id";
    final String name = "name";
    final var mapping =
        new MappingRecord()
            .setMappingKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setId(id);
    mappingState.create(mapping);

    // when
    final var retrievedMapping = mappingState.get(claimName, claimValue);

    // then
    assertThat(retrievedMapping).isPresent();
    assertThat(retrievedMapping.get().getMappingKey()).isEqualTo(key);
    assertThat(retrievedMapping.get().getName()).isEqualTo(name);
    assertThat(retrievedMapping.get().getId()).isEqualTo(id);
  }

  @Test
  void shouldReturnEmptyIfMappingDoesNotExistByClaim() {
    // when
    final var mapping = mappingState.get("claimName", "claimValue");

    // then
    assertThat(mapping).isEmpty();
  }

  @Test
  void shouldRetrieveMappingById() {
    // given
    final long key = 1L;
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final String id = "id";
    final String name = "name";
    final var mapping =
        new MappingRecord()
            .setMappingKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setId(id);
    mappingState.create(mapping);

    // when
    final var retrievedMapping = mappingState.get(id);

    // then
    assertThat(retrievedMapping).isPresent();
    assertThat(retrievedMapping.get().getMappingKey()).isEqualTo(key);
    assertThat(retrievedMapping.get().getName()).isEqualTo(name);
    assertThat(retrievedMapping.get().getClaimName()).isEqualTo(claimName);
    assertThat(retrievedMapping.get().getClaimValue()).isEqualTo(claimValue);
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
    final var tenantId = "tenant";

    // when
    mappingState.addTenant(key, tenantId);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getTenantIdsList()).containsExactly(tenantId);
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
    final var tenantId = "tenant";
    mappingState.addTenant(key, tenantId);

    // when
    mappingState.removeTenant(key, tenantId);

    // then
    final var persistedMapping = mappingState.get(key).get();
    assertThat(persistedMapping.getTenantIdsList()).isEmpty();
  }

  @Test
  void shouldDeleteMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final String id = "id";
    final var mapping =
        new MappingRecord()
            .setId(id)
            .setMappingKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    mappingState.delete(id);

    // then
    assertThat(mappingState.get(key)).isEmpty();
    assertThat(mappingState.get(id)).isEmpty();
    assertThat(mappingState.get(claimName, claimValue)).isEmpty();
  }

  @Test
  void shouldUpdateMapping() {
    // given
    final long key = 1L;
    final String name = "name";
    final String id = "id";
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final var mapping =
        new MappingRecord()
            .setMappingKey(key)
            .setId(id)
            .setName(name)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    final String newName = "newName";
    final String newClaimName = "newClaimName";
    final String newClaimValue = "newClaimValue";
    final var updateMapping =
        new MappingRecord()
            .setMappingKey(key)
            .setId(id)
            .setName(newName)
            .setClaimName(newClaimName)
            .setClaimValue(newClaimValue);
    mappingState.update(updateMapping);

    // then
    assertThat(mappingState.get(id)).isNotEmpty();
    final var mappingById = mappingState.get(id).get();
    assertThat(mappingById.getName()).isEqualTo(newName);
    assertThat(mappingById.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(mappingById.getClaimName()).isEqualTo(newClaimName);

    assertThat(mappingState.get(claimName, claimValue)).isEmpty();
    assertThat(mappingState.get(newClaimName, newClaimValue)).isNotEmpty();
    final var mappingByClaim = mappingState.get(newClaimName, newClaimValue).get();
    assertThat(mappingByClaim.getName()).isEqualTo(newName);
    assertThat(mappingByClaim.getId()).isEqualTo(id);
  }
}
