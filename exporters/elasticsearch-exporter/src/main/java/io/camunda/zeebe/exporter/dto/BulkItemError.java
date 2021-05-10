/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class BulkItemError {

  private String type = "";
  private String reason = "";

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(final String reason) {
    this.reason = reason;
  }
}
