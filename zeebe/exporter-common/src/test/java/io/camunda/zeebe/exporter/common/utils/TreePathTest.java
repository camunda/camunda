/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TreePathTest {

  @Test
  void shouldNotTruncateWhenPathFits() {
    // given
    final var treePath = "PI_123" + "/FN_A" + "/FNI_1";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, 1000);

    // then
    assertThat(truncated).isEqualTo("PI_123/FN_A/FNI_1");
  }

  @Test
  void shouldTruncateWhenTooManySegments() {
    // given
    final var columnSize = 1200;
    final var sb = new StringBuilder();
    sb.append("PI_root");
    for (int i = 0; i < 150; i++) {
      sb.append("/FN_FN").append(i);
      sb.append("/FNI_FNI").append(i);
      sb.append("/PI_PI").append(i + 1);
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated).startsWith("PI_root");
    assertThat(truncated).contains("PI_PI20");
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
  }

  @Test
  void shouldHandleVaryingSegmentLengths() {
    // given
    final var columnSize = 250;
    final var sb = new StringBuilder();
    sb.append("PI_shortRoot");
    sb.append("/FN_A");
    sb.append("/FNI_VeryLongFlowNodeInstanceIdentifier123");
    sb.append("/PI_VeryLongProcessInstanceIdentifier123");
    sb.append("/FN_B");
    sb.append("/FNI_Short");
    sb.append("/PI_ShortProcessInstanceIdentifier");
    sb.append("/FN_C");
    sb.append("/FNI_MediumLengthId");
    sb.append("/PI_MediumLengthProcessInstanceIdentifier");
    for (int i = 0; i < 10; i++) {
      sb.append("/FN_FN").append(i);
      sb.append("/FNI_FNI").append(i);
      sb.append("/PI_PI").append(i + 1);
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
    assertThat(truncated).startsWith("PI_shortRoot");
    assertThat(truncated).contains("PI_PI10"); // last segment
  }

  @Test
  void shouldFallbackToHardTruncateWhenSingleSegmentDoesNotFit() {
    // given
    final var columnSize = 100;
    final var sb = new StringBuilder();
    sb.append("PI_veryLongRootSegmentThatTakesUpSpace");
    for (int i = 0; i < 20; i++) {
      sb.append("/FN_VeryLongFlowNodeName").append(i);
      sb.append("/FNI_VeryLongFlowNodeInstanceInstanceName").append(i);
      sb.append("/PI_VeryLongProcessInstanceId").append(i + 1);
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated).isEqualTo(treePath.substring(0, columnSize));
    assertThat(truncated.length()).isEqualTo(columnSize);
  }

  @Test
  void shouldFallbackToHardTruncateWhenTreePathIsNotParsable() {
    // given
    final var columnSize = 100;
    final var sb = new StringBuilder();
    sb.append("PI_veryLongRootSegmentThatTakesUpSpace");
    for (int i = 0; i < 20; i++) {
      sb.append("/FN_VeryLongFlowNodeName").append(i);
      sb.append("/FNI_VeryLongFlowNodeInstanceInstanceName").append(i);
      // This treePath is invalid as it does not follow the expected PI/FN/FNI pattern
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated).isEqualTo(treePath.substring(0, columnSize));
    assertThat(truncated.length()).isEqualTo(columnSize);
  }

  // Tests for unprefixed (intra-tree) paths

  @Test
  void shouldHandleUnprefixedPathWhenFits() {
    // given
    final var treePath = "123/456/789";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, 1000);

    // then
    assertThat(truncated).isEqualTo("123/456/789");
  }

  @Test
  void shouldTruncateUnprefixedPathWhenTooLong() {
    // given
    final var columnSize = 50;
    final var sb = new StringBuilder();
    sb.append("1234567890"); // first segment
    for (int i = 1; i <= 20; i++) {
      sb.append("/").append("seg" + i);
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
    assertThat(truncated).startsWith("1234567890"); // root preserved
    assertThat(truncated).contains("seg20"); // leaf preserved
  }

  @Test
  void shouldHandleUnprefixedPathWithVaryingSegmentLengths() {
    // given
    final var columnSize = 100;
    final var sb = new StringBuilder();
    sb.append("shortRoot");
    sb.append("/veryLongSegmentIdentifier123456");
    sb.append("/short");
    sb.append("/mediumLength");
    for (int i = 0; i < 10; i++) {
      sb.append("/seg").append(i);
    }
    final var treePath = sb.toString();

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
    assertThat(truncated).startsWith("shortRoot");
    assertThat(truncated).contains("seg9"); // last segment
  }

  @Test
  void shouldHandleSingleSegmentUnprefixedPath() {
    // given
    final var treePath = "1234567890";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, 1000);

    // then
    assertThat(truncated).isEqualTo("1234567890");
  }

  @Test
  void shouldTruncateLongSingleSegmentUnprefixedPath() {
    // given
    final var columnSize = 50;
    final var treePath = "1234567890".repeat(10); // 100 characters

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated).isEqualTo(treePath.substring(0, columnSize));
    assertThat(truncated.length()).isEqualTo(columnSize);
  }

  @Test
  void shouldMaximizeSegmentsForUnprefixedPath() {
    // given
    final var columnSize = 60;
    final var treePath = "a/bb/ccc/dddd/eeeee/ffffff/ggggggg/hhhhhhhh/iiiiiiiii";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
    assertThat(truncated).startsWith("a"); // root preserved
    // Should preserve as many leaf segments as possible
    assertThat(truncated).contains("iiiiiiiii"); // last segment preserved
  }

  @Test
  void shouldTruncateDeterministicallyAndAssertFullResult() {
    // given
    final var columnSize = 60;
    final var treePath =
        "root/seg1/seg2/seg3/seg4/seg5/seg6/seg7/seg8/seg9/seg10/seg11/seg12/seg13/seg14/seg15";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated).isEqualTo("root/seg1/seg2/seg3/seg4/seg5/seg6/seg7/seg8/seg9/seg15");
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
  }

  @Test
  void shouldTruncateDeterministicallyAndAssertFullResultWithPrefixedSegments() {
    // given
    final var columnSize = 80;
    final var treePath =
        "PI_root/FN_a/FNI_1/PI_b/FN_c/FNI_2/PI_d/FN_e/FNI_3/PI_f/FN_g/FNI_4/PI_h/FN_i/FNI_5";

    // when
    final var truncated = TreePathTruncator.truncateTreePath(treePath, columnSize);

    // then
    assertThat(truncated)
        .isEqualTo("PI_root/FN_a/FNI_1/PI_b/FN_c/FNI_2/PI_f/FN_g/FNI_4/PI_h/FN_i/FNI_5");
    assertThat(truncated.length()).isLessThanOrEqualTo(columnSize);
  }
}
