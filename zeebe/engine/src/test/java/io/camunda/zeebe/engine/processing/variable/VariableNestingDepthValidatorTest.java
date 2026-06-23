/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public final class VariableNestingDepthValidatorTest {

  @Test
  public void shouldNotExceedDepthForFlatDocument() {
    // given
    final var buffer = msgPackOf("{\"a\": 1, \"b\": 2}");

    // when / then
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 1)).isFalse();
  }

  @Test
  public void shouldHandleArrayNesting() {
    // given — {"a": [[[1]]]} is 4 levels deep (root map + 3 arrays)
    final var buffer = msgPackOf("{\"a\": [[[1]]]}");

    // when / then
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 4)).isFalse();
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 3)).isTrue();
  }

  @Test
  public void shouldHandleMixedNesting() {
    // given — {"a": [{"b": 1}]} is 3 levels deep (root map + array + inner map)
    final var buffer = msgPackOf("{\"a\": [{\"b\": 1}]}");

    // when / then
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 3)).isFalse();
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 2)).isTrue();
  }

  @Test
  void shouldHandleEmptyNestedContainers() {
    // given — {"a": {}, "b": []} — both inner containers are depth 2
    final var buffer = msgPackOf("{\"a\": {}, \"b\": []}");

    // when / then
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 2)).isFalse();
    assertThat(VariableNestingDepthValidator.exceedsMaxDepth(buffer, 1)).isTrue();
  }

  @Test
  void shouldReturnRightForNullBuffer() {
    // when
    final var result = VariableNestingDepthValidator.validate(-1, null, 1000);

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldReturnRightForEmptyBuffer() {
    // given
    final var buffer = new UnsafeBuffer(new byte[0]);

    // when
    final var result = VariableNestingDepthValidator.validate(-1, buffer, 1000);

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldReturnRightWhenDocumentIsWithinLimit() {
    // given
    final var buffer = msgPackOf(buildNestedJson(1000));

    // when
    final var result = VariableNestingDepthValidator.validate(-1, buffer, 1000);

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldReturnLeftWhenDocumentExceedsLimit() {
    // given
    final var buffer = msgPackOf(buildNestedJson(1001));

    // when
    final var result = VariableNestingDepthValidator.validate(-1, buffer, 1000);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getErrorType()).isEqualTo(ErrorType.IO_MAPPING_ERROR);
    assertThat(result.getLeft().getMessage())
        .isEqualTo(
            VariableNestingDepthValidator.NESTING_DEPTH_EXCEEDED_ERROR_MESSAGE.formatted(1000));
  }

  @Test
  void shouldNotThrowStackOverflowOnPathologicalInput() {
    // given — 50 000 levels deep: validates that the iterative algorithm handles extreme input
    final var buffer = msgPackOf(buildNestedJson(50_000));

    // when / then — must not throw
    final var result = VariableNestingDepthValidator.validate(-1, buffer, 1000);

    assertThat(result.isLeft()).isTrue();

    assertThat(result.getLeft().getErrorType()).isEqualTo(ErrorType.IO_MAPPING_ERROR);
  }

  /**
   * Builds a JSON document with {@code depth} levels of map nesting, e.g. {@code depth=3} produces
   * {@code {"k":{"k":{"k":1}}}}.
   */
  private static String buildNestedJson(final int depth) {
    final var sb = new StringBuilder();
    sb.repeat("{\"k\":", Math.max(0, depth));
    sb.append("1");
    sb.repeat("}", Math.max(0, depth));
    return sb.toString();
  }

  private static UnsafeBuffer msgPackOf(final String json) {
    final byte[] bytes = MsgPackConverter.convertToMsgPack(json);
    return new UnsafeBuffer(bytes);
  }
}
