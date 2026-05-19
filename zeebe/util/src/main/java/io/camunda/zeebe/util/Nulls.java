/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import org.jspecify.annotations.Nullable;

public final class Nulls {
  private Nulls() {}

  @SuppressWarnings("NullAway")
  public static <V extends @Nullable Object> V uncheckedCastToNonNull(@Nullable final V value) {
    return value;
  }
}
