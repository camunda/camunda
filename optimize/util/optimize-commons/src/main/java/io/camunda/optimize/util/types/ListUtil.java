/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.optimize.util.types;

import java.util.Arrays;
import java.util.List;

public class ListUtil {
  @SafeVarargs
  public static <A> List<A> concat(final List<A>... lists) {
    return Arrays.stream(lists).flatMap(List::stream).toList();
  }

  public static <A> List<A> concat(final A item, final List<A> list) {
    return concat(List.of(item), list);
  }
}
