/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import java.util.Optional;

/*
 * Convertable is an Object wrapper which allows for safe type casting
 */

public class Convertable {
  Object value;

  public Convertable(Object value) {
    this.value = value;
  }

  public static Convertable from(Object value) {
    return new Convertable(value);
  }

  public <R> Optional<R> to() {
    try {
      return Optional.ofNullable((R) value);
    } catch (ClassCastException e) {
      return Optional.empty();
    }
  }
}
