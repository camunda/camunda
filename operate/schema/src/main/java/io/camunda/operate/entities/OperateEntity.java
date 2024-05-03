/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

public abstract class OperateEntity<T extends OperateEntity<T>> {

  private String id;

  public String getId() {
    return id;
  }

  public T setId(String id) {
    this.id = id;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final OperateEntity<T> that = (OperateEntity<T>) o;

    return id != null ? id.equals(that.id) : that.id == null;
  }

  @Override
  public String toString() {
    return "OperateEntity{" + "id='" + id + "\'}";
  }
}
