/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class ExpressionLabelTest {

  @Test
  void shouldHaveNoKindWhenNone() {
    assertThat(ExpressionLabel.NONE.hasKind())
        .as("NONE should report that it has no kind")
        .isFalse();
  }

  @Test
  void shouldHaveKindWhenKindIsGiven() {
    // given
    final var label = new ExpressionLabel("condition expression", null);

    // then
    assertThat(label.hasKind()).as("a label with a 'kind' should report having one").isTrue();
  }

  @Test
  void shouldDescribeNoTargetAsEmptyString() {
    // given
    final var label = new ExpressionLabel("condition expression", null);

    // then
    assertThat(label.describeTarget())
        .as("a label without a 'target' should describe no target")
        .isEmpty();
  }

  @Test
  void shouldDescribeTargetWithLeadingSpace() {
    // given
    final var label = new ExpressionLabel("condition expression", "sequence flow 's2'");

    // then
    assertThat(label.describeTarget())
        .as("the target description should be directly appendable after the expression text")
        .isEqualTo(" of sequence flow 's2'");
  }

  @Test
  void shouldAllowKindWithoutTarget() {
    assertThat(new ExpressionLabel("condition expression", null).target())
        .as("a 'kind' without a 'target' is a valid, partial label")
        .isNull();
  }

  @Test
  void shouldRejectTargetWithoutKind() {
    assertThatThrownBy(() -> new ExpressionLabel(null, "sequence flow 's2'"))
        .as("a 'target' without a 'kind' would be dropped inconsistently by its consumers")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected 'target' to be null when 'kind' is null, but 'target' was 'sequence flow 's2''");
  }
}
