/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;

public class TerminatedUserSessionDto {

  private String id;
  private OffsetDateTime terminationTimestamp;

  public TerminatedUserSessionDto(final String id) {
    this.id = id;
    terminationTimestamp = LocalDateUtil.getCurrentDateTime();
  }

  protected TerminatedUserSessionDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public OffsetDateTime getTerminationTimestamp() {
    return terminationTimestamp;
  }

  public void setTerminationTimestamp(final OffsetDateTime terminationTimestamp) {
    this.terminationTimestamp = terminationTimestamp;
  }
}
