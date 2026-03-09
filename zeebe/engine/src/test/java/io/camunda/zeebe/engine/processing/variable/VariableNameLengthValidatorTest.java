/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class VariableNameLengthValidatorTest {

  @Test
  public void shouldAcceptEmptyDocument() {
    // when
    final var result =
        VariableNameLengthValidator.validateVariableNameLength(new UnsafeBuffer(new byte[0]));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  public void shouldAcceptNilValue() {
    // when
    final var result =
        VariableNameLengthValidator.validateVariableNameLength(new UnsafeBuffer(MsgPackHelper.NIL));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  public void shouldRejectVariableNameThatExceedsDefaultLength() {
    // given
    final var variableName = "x".repeat(EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH + 1);

    // when
    final var result =
        VariableNameLengthValidator.validateVariableNameLength(asMsgPack(variableName, 1));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().reason())
        .contains("Expected variable names to be no longer than 32768 characters")
        .contains("length 32769");
  }

  @Test
  public void shouldAcceptWhenVariableNameIsNotString() {
    // given: {1: "a"}
    final byte[] invalidNameTypeDocument = new byte[] {(byte) 0x81, 0x01, (byte) 0xA1, 0x61};

    // when
    final var result =
        VariableNameLengthValidator.validateVariableNameLength(
            new UnsafeBuffer(invalidNameTypeDocument));

    // then
    assertThat(result.isRight()).isTrue();
  }
}
