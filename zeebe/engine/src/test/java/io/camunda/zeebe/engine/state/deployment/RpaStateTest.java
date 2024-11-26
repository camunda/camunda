/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRpaState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class RpaStateTest {
  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableRpaState rpaState;

  @BeforeEach
  public void setup() {
    rpaState = processingState.getRpaState();
  }

  @Test
  void shouldReturnEmptyIfNoRpaIsDeployedForRpaId() {
    // when
    final var persistedRpa = rpaState.findLatestRpaById(wrapString("form-1"), tenantId);

    // then
    assertThat(persistedRpa).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoRpaIsDeployedForRpaKey() {
    // when
    final var persistedRpa = rpaState.findRpaByKey(1L, tenantId);

    // then
    assertThat(persistedRpa).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoRpaIsDeployedForRpaIdAndDeploymentKey() {
    // when
    final var persistedRpa =
        rpaState.findRpaByIdAndDeploymentKey(wrapString("form-1"), 1L, tenantId);

    // then
    assertThat(persistedRpa).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoRpaIsDeployedForRpaIdAndVersionTag() {
    // when
    final var persistedRpa =
        rpaState.findRpaByIdAndVersionTag(wrapString("form-1"), "v1.0", tenantId);

    // then
    assertThat(persistedRpa).isEmpty();
  }
}
