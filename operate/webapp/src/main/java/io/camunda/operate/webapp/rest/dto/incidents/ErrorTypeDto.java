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

public class ErrorTypeDto implements Comparable<ErrorTypeDto> {

  private String id;
  private String name;

  public static ErrorTypeDto createFrom(final ErrorType errorType) {
    return new ErrorTypeDto().setId(errorType.name()).setName(errorType.getTitle());
  }

  public String getId() {
    return id;
  }

  public ErrorTypeDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ErrorTypeDto setName(final String name) {
    this.name = name;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ErrorTypeDto that = (ErrorTypeDto) o;
    return Objects.equals(id, that.id) && Objects.equals(name, that.name);
  }

  @Override
  public String toString() {
    return "ErrorTypeDto{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
  }

  @Override
  public int compareTo(final ErrorTypeDto o) {
    if (id != null) {
      return id.compareTo(o.getId());
    } else {
      return 0;
    }
  }
}
