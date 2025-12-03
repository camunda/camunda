/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallisteners;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenersRecord;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GlobalListenersStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableGlobalListenersState globalListenersState;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Test
  public void shouldCreateFirstConfiguration() {
    // given
    final GlobalListenersRecord expectedConfig =
        new GlobalListenersRecord()
            .setListenersConfigKey(123)
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global1")
                    .setEventTypes(List.of("creating", "assigning"))
                    .setRetries("5"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global2")
                    .setEventTypes(List.of("all"))
                    .setAfterNonGlobal(false));

    // when
    globalListenersState.updateCurrentConfiguration(expectedConfig);

    // then
    final var storedConfig = globalListenersState.getCurrentConfig().orElse(null);
    assertThat(storedConfig).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldUpdateConfiguration() {
    // given
    final GlobalListenersRecord firstConfig =
        new GlobalListenersRecord()
            .setListenersConfigKey(123)
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global1")
                    .setEventTypes(List.of("creating", "assigning"))
                    .setRetries("5"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global2")
                    .setEventTypes(List.of("all"))
                    .setAfterNonGlobal(false));
    globalListenersState.updateCurrentConfiguration(firstConfig);

    final GlobalListenersRecord newConfig =
        new GlobalListenersRecord()
            .setListenersConfigKey(321)
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global1")
                    .setEventTypes(List.of("creating", "assigning"))
                    .setRetries("3"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global3")
                    .setEventTypes(List.of("creating"))
                    .setAfterNonGlobal(true));

    // when
    globalListenersState.updateCurrentConfiguration(newConfig);

    // then
    final var storedConfig = globalListenersState.getCurrentConfig().orElse(null);
    assertThat(storedConfig).isNotNull().isEqualTo(newConfig);
  }
}
