/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TreePathParserTest {

  @Test
  void shouldExtractProcessInstanceKeysFromValidTreePath() {
    // given
    final String treePath = "PI_1/FN_1/FNI_1/PI_2/FN_3/FNI_3";

    // when
    final List<Long> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).containsExactly(1L, 2L); // Extracts all process instance keys in order
  }

  @Test
  void shouldReturnEmptyListForTreePathWithoutProcessInstances() {
    // given
    final String treePath = "FN_1/FNI_1/FN_3/FNI_3";

    // when
    final List<Long> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).isEmpty(); // No PI_x patterns present
  }

  @Test
  void shouldHandleTreePathWithSingleProcessInstance() {
    // given
    final String treePath = "PI_123";

    // when
    final List<Long> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).containsExactly(123L); // Only one process instance key
  }

  @Test
  void shouldReturnEmptyListForEmptyTreePath() {
    // given
    final String treePath = "";

    // when
    final List<Long> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).isEmpty(); // No valid content in a tree path
  }
}
