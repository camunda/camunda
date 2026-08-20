/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

public class SbeUtil {
  public static BooleanType toSBE(final boolean value) {
    return value ? BooleanType.TRUE : BooleanType.FALSE;
  }

  public static boolean toBoolean(final BooleanType booleanType) {
    // safe to use == as it's an enum
    return BooleanType.TRUE == booleanType;
  }
}
