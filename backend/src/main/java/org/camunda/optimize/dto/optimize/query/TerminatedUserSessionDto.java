/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import java.time.OffsetDateTime;

public class TerminatedUserSessionDto {
  private String id;
  private OffsetDateTime terminationTimestamp;

  protected TerminatedUserSessionDto() {
  }

  public TerminatedUserSessionDto(final String id) {
    this(id, OffsetDateTime.now());
  }

  public TerminatedUserSessionDto(final String id, final OffsetDateTime terminationTimestamp) {
    this.id = id;
    this.terminationTimestamp = terminationTimestamp;
  }

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
