/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class UnifiedConfigurationException extends RuntimeException {
  public UnifiedConfigurationException(final Exception cause) {
    super(cause.getMessage(), cause);
  }

  public UnifiedConfigurationException(final String message) {
    super(message);
  }
}
