/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.cmd.InvalidVariableRequestException;
import org.junit.jupiter.api.Test;

final class BrokerVariableNameLengthValidationTest {

  private static final int MAX_VARIABLE_NAME_LENGTH = 5;

  @Test
  void shouldRejectCompleteJobWithTooLongVariableName() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCompleteJobRequest(
                    1L, asMsgPack("x".repeat(MAX_VARIABLE_NAME_LENGTH + 1), 1), null, 5))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("Expected variable names to be no longer than 5 characters")
        .hasMessageContaining("length 6");
  }

  @Test
  void shouldRejectCorrelateMessageWithTooLongVariableName() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCorrelateMessageRequest("message", "correlation-key", 5)
                    .setVariables(asMsgPack("x".repeat(MAX_VARIABLE_NAME_LENGTH + 1), 1)))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("Expected variable names to be no longer than 5 characters")
        .hasMessageContaining("length 6");
  }

  @Test
  void shouldRejectCreateProcessInstanceWithTooLongVariableName() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCreateProcessInstanceRequest(5)
                    .setVariables(asMsgPack("x".repeat(MAX_VARIABLE_NAME_LENGTH + 1), 1)))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("Expected variable names to be no longer than 5 characters")
        .hasMessageContaining("length 6");
  }

  @Test
  void shouldRejectCompleteUserTaskWithTooLongVariableName() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerUserTaskCompletionRequest(
                    1L, asMsgPack("x".repeat(MAX_VARIABLE_NAME_LENGTH + 1), 1), "", 5))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("Expected variable names to be no longer than 5 characters")
        .hasMessageContaining("length 6");
  }

  @Test
  void shouldAllowValidVariableNamesForCreateProcessInstanceWithResult() {
    // when/then
    assertThatCode(
            () ->
                new BrokerCreateProcessInstanceWithResultRequest(5)
                    .setVariables(asMsgPack("x".repeat(MAX_VARIABLE_NAME_LENGTH), 1)))
        .doesNotThrowAnyException();
  }
}
