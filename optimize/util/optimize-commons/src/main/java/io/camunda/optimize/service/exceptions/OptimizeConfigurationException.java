/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;

@Getter
public class OptimizeConfigurationException extends OptimizeRuntimeException {
  private Map<String, String> deletedKeysAndDocumentationLink = Collections.emptyMap();

  public OptimizeConfigurationException(String message) {
    super(message);
  }

  public OptimizeConfigurationException(String message, Exception e) {
    super(message, e);
  }

  public OptimizeConfigurationException(
      String message, Map<String, String> deletedKeysAndDocumentationLink) {
    super(message);
    this.deletedKeysAndDocumentationLink =
        Optional.ofNullable(deletedKeysAndDocumentationLink).orElse(Collections.emptyMap());
  }
}
