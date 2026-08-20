/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AdHocSubProcessInstructionCompletedApplierTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private AdHocSubProcessInstructionCompletedApplier applier;

  @BeforeEach
  public void setup() {
    elementInstanceState = processingState.getElementInstanceState();
    applier = new AdHocSubProcessInstructionCompletedApplier(elementInstanceState);
  }

  @Test
  void shouldUpdateCompletionConditionFulfilled() {
    // Given
    final long adHocSubProcessInstanceKey = 1L;
    final boolean completionConditionFulfilled = true;
    elementInstanceState.createInstance(
        new ElementInstance(
            adHocSubProcessInstanceKey,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new ProcessInstanceRecord()));

    // When
    applier.applyState(
        adHocSubProcessInstanceKey,
        new AdHocSubProcessInstructionRecord()
            .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .setCompletionConditionFulfilled(completionConditionFulfilled));

    // Then
    final var instance = elementInstanceState.getInstance(adHocSubProcessInstanceKey);
    assertThat(instance.isCompletionConditionFulfilled()).isTrue();
  }
}
