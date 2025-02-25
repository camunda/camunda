/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.utils;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExportUtilTest {

  @Test
  void shouldBuildTreePathWithElementInstancePath() {
    // given
    final Long key = 456L;
    final Long processInstanceKey = 123L;
    final var elementInstancePath = List.of(List.of(1L, 2L, 3L));

    // when
    final String treePath = ExportUtil.buildTreePath(key, processInstanceKey, elementInstancePath);

    // then
    assertThat(treePath).isEqualTo("1/2/3");
  }

  @Test
  void shouldBuildTreePathWithElementInstancePathForLastProcessOnly() {
    // given
    final Long key = 456L;
    final Long processInstanceKey = 123L;
    final var elementInstancePath = List.of(List.of(1L, 2L, 3L), List.of(4L, 5L));

    // when
    final String treePath = ExportUtil.buildTreePath(key, processInstanceKey, elementInstancePath);

    // then
    assertThat(treePath).isEqualTo("4/5");
  }

  @Test
  void shouldBuildTreePathWithoutElementInstancePath() {
    // given
    final Long key = 456L;
    final Long processInstanceKey = 123L;
    final List<List<Long>> elementInstancePath = null;

    // when
    final String treePath = ExportUtil.buildTreePath(key, processInstanceKey, elementInstancePath);

    // then
    assertThat(treePath).isEqualTo("123/456");
  }

  @Test
  void shouldBuildTreePathWithEmptyElementInstancePath() {
    // given
    final Long key = 456L;
    final Long processInstanceKey = 123L;
    final List<List<Long>> elementInstancePath = emptyList();

    // when
    final String treePath = ExportUtil.buildTreePath(key, processInstanceKey, elementInstancePath);

    // then
    assertThat(treePath).isEqualTo("123/456");
  }
}
