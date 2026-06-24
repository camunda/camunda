/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

public class DataUtil {

  @SafeVarargs
  public static <T> HashSet<T> newHashSet(final T... elements) {
    Objects.requireNonNull(elements);
    return new HashSet(Arrays.asList(elements));
  }
}
