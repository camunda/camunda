/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

public class TruncateUtil {

  public static String truncateValue(final String value, final int sizeLimit) {
    var truncatedValue = value;

    if (truncatedValue.length() > sizeLimit) {
      truncatedValue = truncatedValue.substring(0, sizeLimit);
    }

    return truncatedValue;
  }
}
