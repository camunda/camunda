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

public class OptimizeConfigurationException extends OptimizeRuntimeException {

  private Map<String, String> deletedKeysAndDocumentationLink = Collections.emptyMap();

  public OptimizeConfigurationException(final String message) {
    super(message);
  }

  public OptimizeConfigurationException(final String message, final Exception e) {
    super(message, e);
  }

  public OptimizeConfigurationException(
      final String message, final Map<String, String> deletedKeysAndDocumentationLink) {
    super(message);
    this.deletedKeysAndDocumentationLink =
        Optional.ofNullable(deletedKeysAndDocumentationLink).orElse(Collections.emptyMap());
  }

  public Map<String, String> getDeletedKeysAndDocumentationLink() {
    return deletedKeysAndDocumentationLink;
  }
}
