/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class MessageEventValidationTest {

  @Test
  @DisplayName("message start event with no message reference")
  public void shouldRejectDeploymentWhenNoMessageReferenced() {

    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("processId")
            .startEvent("startEvent")
            .messageEventDefinition()
            .id("messageEvent")
            .done();

    ProcessValidationUtil.validateProcess(
        instance,
        ExpectedValidationResult.expect(MessageEventDefinition.class, "Must reference a message"));
  }
}
