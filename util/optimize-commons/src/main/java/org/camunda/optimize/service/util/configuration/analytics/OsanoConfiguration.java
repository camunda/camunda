/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OsanoConfiguration {
  @JsonProperty("scriptUrl")
  private String scriptUrl;

  public Optional<String> getScriptUrl() {
    return Optional.ofNullable(scriptUrl);
  }
}
