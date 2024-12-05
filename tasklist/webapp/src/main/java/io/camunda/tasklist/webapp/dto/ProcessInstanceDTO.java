/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import java.util.Objects;

public class ProcessInstanceDTO {

  private Long id;

  public Long getId() {
    return id;
  }

  public ProcessInstanceDTO setId(final Long id) {
    this.id = id;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstanceDTO that = (ProcessInstanceDTO) o;
    return Objects.equals(id, that.id);
  }
}
