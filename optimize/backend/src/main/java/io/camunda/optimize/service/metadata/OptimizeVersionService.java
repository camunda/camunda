/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metadata;

import org.springframework.stereotype.Component;

@Component
public class OptimizeVersionService {

  private final String rawVersion;
  private final String version;
  private final String docsVersion;

  public OptimizeVersionService() {
    rawVersion = Version.RAW_VERSION;
    version = Version.VERSION;
    docsVersion = Version.VERSION;
  }

  public OptimizeVersionService(
      final String rawVersion, final String version, final String docsVersion) {
    this.rawVersion = rawVersion;
    this.version = version;
    this.docsVersion = docsVersion;
  }

  public String getRawVersion() {
    return rawVersion;
  }

  public String getVersion() {
    return version;
  }

  public String getDocsVersion() {
    return docsVersion;
  }
}
