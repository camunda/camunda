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
}
