/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import org.springframework.stereotype.Component;

@Component
public class OptimizeVersionService {

  private final String rawVersion;
  private final String version;

  public OptimizeVersionService() {
    this.rawVersion = Version.RAW_VERSION;
    this.version = Version.VERSION;
  }

  public String getVersion() {
    return version;
  }

  public String getRawVersion() {
    return rawVersion;
  }

}
