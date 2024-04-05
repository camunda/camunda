/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
