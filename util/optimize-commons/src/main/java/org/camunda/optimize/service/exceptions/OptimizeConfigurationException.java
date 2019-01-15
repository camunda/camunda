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
