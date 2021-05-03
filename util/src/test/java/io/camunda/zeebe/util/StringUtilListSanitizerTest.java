/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class StringUtilListSanitizerTest {

  @Test
  public void shouldReturnEmptyListForNull() {
    // given
    final List<String> input = null;
    final List<String> expected = Collections.emptyList();

    // when
    final List<String> actual = StringUtil.LIST_SANITIZER.apply(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldRemoveNulls() {
    // given
    final List<String> input = Arrays.asList("foo", null);
    final List<String> expected = Arrays.asList("foo");

    // when
    final List<String> actual = StringUtil.LIST_SANITIZER.apply(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldRemoveEmptyStrings() {
    // given
    final List<String> input = Arrays.asList("foo", "", "   ");
    final List<String> expected = Arrays.asList("foo");

    // when
    final List<String> actual = StringUtil.LIST_SANITIZER.apply(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldReturnTrimmedEntries() {
    // given
    final List<String> input = Arrays.asList("foo ", " bar");
    final List<String> expected = Arrays.asList("foo", "bar");

    // when
    final List<String> actual = StringUtil.LIST_SANITIZER.apply(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }
}
