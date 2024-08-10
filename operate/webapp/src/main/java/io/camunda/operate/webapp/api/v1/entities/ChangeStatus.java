/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import java.util.Objects;

public class ChangeStatus {

  private String message;
  private Long deleted;

  public String getMessage() {
    return message;
  }

  public ChangeStatus setMessage(final String message) {
    this.message = message;
    return this;
  }

  public Long getDeleted() {
    return deleted;
  }

  public ChangeStatus setDeleted(final long deleted) {
    this.deleted = deleted;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, deleted);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ChangeStatus status = (ChangeStatus) o;
    return deleted.equals(status.deleted) && Objects.equals(message, status.message);
  }

  @Override
  public String toString() {
    return "ChangeStatus{" + "message='" + message + '\'' + ", deleted=" + deleted + '}';
  }
}
