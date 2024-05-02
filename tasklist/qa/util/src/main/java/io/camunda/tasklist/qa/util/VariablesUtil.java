/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import java.util.Random;

public abstract class VariablesUtil {

  public static final String VAR_SUFFIX = "9999999999";

  public static String createBigVariable(int size, String suffix) {
    final Random random = new Random();
    final StringBuffer sb = new StringBuffer();
    for (int i = 0; i < size; i++) {
      sb.append(random.nextInt(9));
    }
    return sb.toString() + suffix;
  }

  public static String createBigVariable(int size) {
    return createBigVariable(size, "");
  }

  public static String createBigVariableWithSuffix(int size) {
    return createBigVariable(size, VAR_SUFFIX);
  }
}
