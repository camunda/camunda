/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.suspender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Process;
import org.junit.jupiter.api.Test;

/**
 * Offline smoke test for {@code bpmn/suspend_target.bpmn}: parses and validates the heavy target
 * model with the Zeebe BPMN model API (no engine, no client), so a malformed model or broken
 * reference is caught in a plain unit test. A full deployment smoke against a real engine lives in
 * {@code io.camunda.zeebe.it.SuspendTargetDeploymentIT}.
 */
class SuspendTargetModelTest {

  @Test
  void shouldParseAndValidateSuspendTargetModel() {
    // given / when - the model is read from the classpath
    final var model =
        Bpmn.readModelFromStream(
            SuspendTargetModelTest.class.getResourceAsStream("/bpmn/suspend_target.bpmn"));

    // then - it validates (schema + references) and exposes the suspendTarget process
    assertThatCode(() -> Bpmn.validateModel(model)).doesNotThrowAnyException();
    assertThat(model.getModelElementsByType(Process.class))
        .extracting(Process::getId)
        .contains("suspendTarget");
  }
}
