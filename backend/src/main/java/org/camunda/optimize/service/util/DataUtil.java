/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

public class DataUtil {

  @SafeVarargs
  public static <T> HashSet<T> newHashSet(T... elements) {
    Objects.requireNonNull(elements);
    return new HashSet(Arrays.asList(elements));
  }

}
