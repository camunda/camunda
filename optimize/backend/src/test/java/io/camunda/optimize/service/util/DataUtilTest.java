/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

public class DataUtilTest {

  @Test
  public void shouldReturnHashSetWithMultipleElements() {
    // when
    final HashSet<String> result = DataUtil.newHashSet("apple", "banana", "cherry");

    // then
    assertThat(result).isNotNull().hasSize(3).contains("apple", "banana", "cherry");
  }

  @Test
  public void shouldDeduplicateDuplicateElements() {
    // when
    final HashSet<String> result =
        DataUtil.newHashSet("apple", "banana", "apple", "cherry", "banana");

    // then
    assertThat(result).isNotNull().hasSize(3).contains("apple", "banana", "cherry");
  }

  @Test
  public void shouldReturnEmptyHashSet() {
    // when
    final HashSet<String> result = DataUtil.newHashSet();

    // then
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  public void shouldReturnHashSetWithSingleElement() {
    // when
    final HashSet<String> result = DataUtil.newHashSet("apple");

    // then
    assertThat(result).isNotNull().hasSize(1).contains("apple");
  }

  @Test
  public void shouldThrowExceptionForNullArray() {
    // then
    assertThatThrownBy(() -> DataUtil.newHashSet((String[]) null))
        .isInstanceOf(NullPointerException.class);
  }
}
