package org.camunda.optimize.service.exceptions;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class OptimizeConfigurationException extends OptimizeRuntimeException {
  private final Map<String, String> deprecatedKeysAndDocumentationLink;

  public OptimizeConfigurationException(String message) {
    this(message, null);
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
