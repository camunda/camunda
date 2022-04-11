/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class OptimizeConfigurationException extends OptimizeRuntimeException {
  private Map<String, String> deprecatedKeysAndDocumentationLink = Collections.emptyMap();

  public OptimizeConfigurationException(String message) {
    super(message);
  }

  public OptimizeConfigurationException(String message, Exception e) {
    super(message, e);
  }

  public OptimizeConfigurationException(String message, Map<String, String> deprecatedKeysAndDocumentationLink) {
    super(message);
    this.deprecatedKeysAndDocumentationLink = Optional.ofNullable(deprecatedKeysAndDocumentationLink)
      .orElse(Collections.emptyMap());
  }

  public Map<String, String> getDeprecatedKeysAndDocumentationLink() {
    return deprecatedKeysAndDocumentationLink;
  }
}
