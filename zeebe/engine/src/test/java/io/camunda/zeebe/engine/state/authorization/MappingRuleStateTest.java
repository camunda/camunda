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
public class MappingRuleStateTest {

  private MutableProcessingState processingState;
  private MutableMappingRuleState mappingRuleState;

  @BeforeEach
  public void setup() {
    mappingRuleState = processingState.getMappingRuleState();
  }

  @Test
  void shouldCreateMapping() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final String name = "name";
    final String mappingRuleId = "mappingRuleId";
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingRuleId)
            .setClaimName(claimName)
            .setName(name)
            .setClaimValue(claimValue);

    // when
    mappingRuleState.create(mappingRule);

    // then
    final var persistedMappingRule = mappingRuleState.get(mappingRuleId).get();
    assertThat(persistedMappingRule.getMappingRuleId()).isEqualTo(mappingRuleId);
    assertThat(persistedMappingRule.getMappingRuleKey()).isEqualTo(key);
    assertThat(persistedMappingRule.getName()).isEqualTo(name);
    assertThat(persistedMappingRule.getClaimName()).isEqualTo(claimName);
    assertThat(persistedMappingRule.getClaimValue()).isEqualTo(claimValue);
  }

  @Test
  void shouldReturnEmptyIfMappingRuleDoesNotExist() {
    // when
    final var mappingRule = mappingRuleState.get("someMappingId");

    // then
    assertThat(mappingRule).isEmpty();
  }

  @Test
  void shouldRetrieveMappingRuleByClaims() {
    // given
    final long key = 1L;
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final String mappingRuleId = "mappingRuleId";
    final String name = "name";
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setMappingRuleId(mappingRuleId);
    mappingRuleState.create(mappingRule);

    // when
    final var retrievedMappingRule = mappingRuleState.get(claimName, claimValue);

    // then
    assertThat(retrievedMappingRule).isPresent();
    assertThat(retrievedMappingRule.get().getName()).isEqualTo(name);
    assertThat(retrievedMappingRule.get().getMappingRuleId()).isEqualTo(mappingRuleId);
    assertThat(retrievedMappingRule.get().getMappingRuleKey()).isEqualTo(key);
  }

  @Test
  void shouldReturnEmptyIfMappingRuleDoesNotExistByClaim() {
    // when
    final var mappingRule = mappingRuleState.get("claimName", "claimValue");

    // then
    assertThat(mappingRule).isEmpty();
  }

  @Test
  void shouldRetrieveMappingRuleById() {
    // given
    final long key = 1L;
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final String mappingRuleId = "mappingRuleId";
    final String name = "name";
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(name)
            .setMappingRuleId(mappingRuleId);
    mappingRuleState.create(mappingRule);

    // when
    final var retrievedMappingRule = mappingRuleState.get(mappingRuleId);

    // then
    assertThat(retrievedMappingRule).isPresent();
    assertThat(retrievedMappingRule.get().getMappingRuleId()).isEqualTo(mappingRuleId);
    assertThat(retrievedMappingRule.get().getName()).isEqualTo(name);
    assertThat(retrievedMappingRule.get().getClaimName()).isEqualTo(claimName);
    assertThat(retrievedMappingRule.get().getClaimValue()).isEqualTo(claimValue);
  }

  @Test
  void shouldDeleteMappingRule() {
    // given
    final long key = 1L;
    final String claimName = "foo";
    final String claimValue = "bar";
    final String mappingRuleId = "mappingRuleId";
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleId(mappingRuleId)
            .setMappingRuleKey(key)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingRuleState.create(mappingRule);

    // when
    mappingRuleState.delete(mappingRuleId);

    // then
    assertThat(mappingRuleState.get(mappingRuleId)).isEmpty();
    assertThat(mappingRuleState.get(claimName, claimValue)).isEmpty();
  }

  @Test
  void shouldUpdateMappingRule() {
    // given
    final long key = 1L;
    final String name = "name";
    final String mappingRuleId = "mappingRuleId";
    final String claimName = "claimName";
    final String claimValue = "claimValue";
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingRuleId)
            .setName(name)
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingRuleState.create(mappingRule);

    // when
    final String newName = "newName";
    final String newClaimName = "newClaimName";
    final String newClaimValue = "newClaimValue";
    final var updateMappingRule =
        new MappingRuleRecord()
            .setMappingRuleKey(key)
            .setMappingRuleId(mappingRuleId)
            .setName(newName)
            .setClaimName(newClaimName)
            .setClaimValue(newClaimValue);
    mappingRuleState.update(updateMappingRule);

    // then
    assertThat(mappingRuleState.get(mappingRuleId)).isNotEmpty();
    final var mappingRuleById = mappingRuleState.get(mappingRuleId).get();
    assertThat(mappingRuleById.getName()).isEqualTo(newName);
    assertThat(mappingRuleById.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(mappingRuleById.getClaimName()).isEqualTo(newClaimName);

    assertThat(mappingRuleState.get(claimName, claimValue)).isEmpty();
    assertThat(mappingRuleState.get(newClaimName, newClaimValue)).isNotEmpty();
    final var mappingByClaim = mappingRuleState.get(newClaimName, newClaimValue).get();
    assertThat(mappingByClaim.getName()).isEqualTo(newName);
    assertThat(mappingByClaim.getMappingRuleId()).isEqualTo(mappingRuleId);
  }
}
