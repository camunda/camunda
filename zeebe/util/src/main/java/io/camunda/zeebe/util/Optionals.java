/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.Optional;
import java.util.OptionalLong;

public class Optionals {
  public static Optional<Long> boxed(final OptionalLong optionalLong) {
    if (optionalLong.isPresent()) {
      return Optional.of(optionalLong.getAsLong());
    }
    return Optional.empty();
  }
}
