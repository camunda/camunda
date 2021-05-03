/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging.stackdriver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The service context is used to properly aggregate errors in the Stackdriver Error Reporting tool.
 *
 * <p>Errors are grouped by service name and service version, allowing us to track which versions
 * and clusters are affected by a set of errors.
 *
 * <p>https://cloud.google.com/error-reporting/docs/formatting-error-messages
 */
@JsonInclude(Include.NON_EMPTY)
final class ServiceContext {
  @JsonProperty("service")
  private String service;

  @JsonProperty("version")
  private String version;

  public String getService() {
    return service;
  }

  public void setService(final String service) {
    this.service = service;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }
}
