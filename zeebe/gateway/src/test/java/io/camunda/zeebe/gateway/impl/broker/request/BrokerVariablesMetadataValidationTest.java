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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class BrokerVariablesMetadataValidationTest {

  private static final Map<String, VariableMetadataRecord> METADATA =
      Map.of("ssn", protectedMetadata());

  private static VariableMetadataRecord protectedMetadata() {
    return new VariableMetadataRecord().setProtectionModes(List.of(ProtectionMode.REDACT));
  }

  @Test
  void shouldRejectCreateProcessInstanceMetadataOfUnknownVariable() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCreateProcessInstanceRequest()
                    .setVariables(asMsgPack("name", "x"))
                    .setVariablesMetadata(METADATA))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("only describe variables set in the request")
        .hasMessageContaining("'ssn'");
  }

  @Test
  void shouldRejectCreateProcessInstanceWithResultMetadataOfUnknownVariable() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCreateProcessInstanceWithResultRequest()
                    .setVariables(asMsgPack("name", "x"))
                    .setVariablesMetadata(METADATA))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("'ssn'");
  }

  @Test
  void shouldRejectSetVariablesMetadataOfUnknownVariable() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerSetVariablesRequest()
                    .setVariables(asMsgPack("name", "x"))
                    .setVariablesMetadata(METADATA))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("'ssn'");
  }

  @Test
  void shouldRejectMetadataWhenVariablesAreEmpty() {
    // when/then
    assertThatThrownBy(
            () ->
                new BrokerSetVariablesRequest()
                    .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}")))
                    .setVariablesMetadata(METADATA))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageContaining("'ssn'");
  }

  @Test
  void shouldListAllUnknownVariablesSorted() {
    // given
    final var metadata =
        Map.of("b", protectedMetadata(), "a", protectedMetadata(), "name", protectedMetadata());

    // when/then
    assertThatThrownBy(
            () ->
                new BrokerCreateProcessInstanceRequest()
                    .setVariables(asMsgPack("name", "x"))
                    .setVariablesMetadata(metadata))
        .isInstanceOf(InvalidVariableRequestException.class)
        .hasMessageEndingWith("'a', 'b'");
  }

  @Test
  void shouldAcceptMetadataOfSetVariables() {
    // when/then
    assertThatCode(
            () ->
                new BrokerCreateProcessInstanceRequest()
                    .setVariables(asMsgPack("ssn", "123"))
                    .setVariablesMetadata(METADATA))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                new BrokerSetVariablesRequest()
                    .setVariables(asMsgPack("ssn", "123"))
                    .setVariablesMetadata(METADATA))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptEmptyMetadataWithoutVariables() {
    // when/then
    assertThatCode(() -> new BrokerSetVariablesRequest().setVariablesMetadata(Map.of()))
        .doesNotThrowAnyException();
  }
}
