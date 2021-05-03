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
public final class PutIndexTemplateResponse {

  private boolean acknowledged;

  public boolean isAcknowledged() {
    return acknowledged;
  }

  public void setAcknowledged(final boolean acknowledged) {
    this.acknowledged = acknowledged;
  }
}
