/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
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
}
