/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class MappingStateTest {

  private MutableProcessingState processingState;
  private MutableMappingRuleState mappingState;

  @BeforeEach
  public void setup() {
    mappingState = processingState.getMappingRuleState();
  }

  @Test
  void shouldCreateMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final String name = "name";
    final String mappingId = "mappingId";
    final var mapping =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingId)
            .setClaimName(claimName)
            .setName(name)
            .setClaimValue(claimValue);

    // when
    mappingState.create(mapping);

    // then
    final var persistedMapping = mappingState.get(mappingId).get();
    assertThat(persistedMapping.getMappingRuleId()).isEqualTo(mappingId);
    assertThat(persistedMapping.getMappingRuleKey()).isEqualTo(key);
    assertThat(persistedMapping.getName()).isEqualTo(name);
    assertThat(persistedMapping.getClaimName()).isEqualTo(claimName);
    assertThat(persistedMapping.getClaimValue()).isEqualTo(claimValue);
  }

  @Test
  void shouldReturnEmptyIfMappingDoesNotExist() {
    // when
    final var mapping = mappingState.get("someMappingId");

    // then
    assertThat(mapping).isEmpty();
  }

  @Test
  void shouldRetrieveMappingByClaims() {
    // given
    final long key = 1L;
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final String mappingId = "mappingId";
    final String name = "name";
    final var mapping =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setMappingRuleId(mappingId);
    mappingState.create(mapping);

    // when
    final var retrievedMapping = mappingState.get(claimName, claimValue);

    // then
    assertThat(retrievedMapping).isPresent();
    assertThat(retrievedMapping.get().getName()).isEqualTo(name);
    assertThat(retrievedMapping.get().getMappingRuleId()).isEqualTo(mappingId);
    assertThat(retrievedMapping.get().getMappingRuleKey()).isEqualTo(key);
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
    final String mappingId = "mappingId";
    final String name = "name";
    final var mapping =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setMappingRuleId(mappingId);
    mappingState.create(mapping);

    // when
    final var retrievedMapping = mappingState.get(mappingId);

    // then
    assertThat(retrievedMapping).isPresent();
    assertThat(retrievedMapping.get().getMappingRuleId()).isEqualTo(mappingId);
    assertThat(retrievedMapping.get().getName()).isEqualTo(name);
    assertThat(retrievedMapping.get().getClaimName()).isEqualTo(claimName);
    assertThat(retrievedMapping.get().getClaimValue()).isEqualTo(claimValue);
  }

  @Test
  void shouldDeleteMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final String mappingId = "mappingId";
    final var mapping =
        new MappingRuleRecord()
            .setMappingRuleId(mappingId)
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    mappingState.delete(mappingId);

    // then
    assertThat(mappingState.get(mappingId)).isEmpty();
    assertThat(mappingState.get(claimName, claimValue)).isEmpty();
  }

  @Test
  void shouldUpdateMapping() {
    // given
    final long key = 1L;
    final String name = "name";
    final String mappingId = "mappingId";
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final var mapping =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingId)
            .setName(name)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingState.create(mapping);

    // when
    final String newName = "newName";
    final String newClaimName = "newClaimName";
    final String newClaimValue = "newClaimValue";
    final var updateMapping =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingId)
            .setName(newName)
            .setClaimName(newClaimName)
            .setClaimValue(newClaimValue);
    mappingState.update(updateMapping);

    // then
    assertThat(mappingState.get(mappingId)).isNotEmpty();
    final var mappingById = mappingState.get(mappingId).get();
    assertThat(mappingById.getName()).isEqualTo(newName);
    assertThat(mappingById.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(mappingById.getClaimName()).isEqualTo(newClaimName);

    assertThat(mappingState.get(claimName, claimValue)).isEmpty();
    assertThat(mappingState.get(newClaimName, newClaimValue)).isNotEmpty();
    final var mappingByClaim = mappingState.get(newClaimName, newClaimValue).get();
    assertThat(mappingByClaim.getName()).isEqualTo(newName);
    assertThat(mappingByClaim.getMappingRuleId()).isEqualTo(mappingId);
  }
}
