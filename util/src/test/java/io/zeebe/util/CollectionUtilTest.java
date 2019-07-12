/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CollectionUtilTest {

  @Test
  public void addToMapOfLists() {
    // given
    final Map<String, List<String>> map = new HashMap<>();

    // when
    CollectionUtil.addToMapOfLists(map, "foo", "bar");

    // then
    assertThat(map).containsExactly(entry("foo", Arrays.asList("bar")));
  }

  @Test
  public void appendToMapOfLists() {
    // given
    final Map<String, List<String>> map = new HashMap<>();
    CollectionUtil.addToMapOfLists(map, "foo", "bar");

    // when
    CollectionUtil.addToMapOfLists(map, "foo", "baz");

    // then
    assertThat(map).containsExactly(entry("foo", Arrays.asList("bar", "baz")));
  }
}
