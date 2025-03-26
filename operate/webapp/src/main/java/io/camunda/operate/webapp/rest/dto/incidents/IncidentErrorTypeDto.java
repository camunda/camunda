/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import io.camunda.webapps.schema.entities.incident.ErrorType;
import java.util.Objects;

public class IncidentErrorTypeDto {

  private String id;

  private String name;

  private int count;

  public static IncidentErrorTypeDto createFrom(final ErrorType errorType) {
    return new IncidentErrorTypeDto().setId(errorType.name()).setName(errorType.getTitle());
  }

  public String getId() {
    return id;
  }

  public IncidentErrorTypeDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public IncidentErrorTypeDto setName(final String name) {
    this.name = name;
    return this;
  }

  public int getCount() {
    return count;
  }

  public IncidentErrorTypeDto setCount(final int count) {
    this.count = count;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, count);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentErrorTypeDto that = (IncidentErrorTypeDto) o;
    return count == that.count && Objects.equals(id, that.id) && Objects.equals(name, that.name);
  }
}
