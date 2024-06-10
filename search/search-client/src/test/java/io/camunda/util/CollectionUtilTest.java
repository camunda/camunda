/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class CollectionUtilTest {

  @Test
  public void shouldRemoveNullValuesFromStringArray() {
    // given
    final String[] values = new String[] {"foo", null, "bar"};

    // when
    final var result = CollectionUtil.withoutNull(values);

    // then
    assertThat(result).hasSize(2);
  }

  @Test
  public void shouldRemoveNullValuesFromCollection() {
    // given
    final var values = new ArrayList<String>();
    values.add("foo");
    values.add(null);
    values.add("bar");

    // when
    final var result = CollectionUtil.withoutNull(values);

    // then
    assertThat(result).hasSize(2);
  }
}
