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

import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class FormStateTest {

  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableFormState formState;

  @BeforeEach
  public void setup() {
    formState = processingState.getFormState();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormId() {
    // when
    final var persistedForm = formState.findLatestFormById(wrapString("form-1"), tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormKey() {
    // when
    final var persistedForm = formState.findFormByKey(1L, tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormIdAndDeploymentKey() {
    // when
    final var persistedForm =
        formState.findFormByIdAndDeploymentKey(wrapString("form-1"), 1L, tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }
}
