/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import io.camunda.operate.entities.ErrorType;
import java.util.Objects;

public class IncidentErrorTypeDto {

  private String id;

  private String name;

  private int count;

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

  public static IncidentErrorTypeDto createFrom(ErrorType errorType) {
    return new IncidentErrorTypeDto().setId(errorType.name()).setName(errorType.getTitle());
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
    return count == that.count &&
        Objects.equals(id, that.id) &&
        Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, count);
  }
}
