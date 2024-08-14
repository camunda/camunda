/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.entities;

public abstract class ExporterEntity<T extends ExporterEntity<T>> {

  private String id;

  public String getId() {
    return id;
  }

  public T setId(final String id) {
    this.id = id;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ExporterEntity<T> that = (ExporterEntity<T>) o;

    return id != null ? id.equals(that.id) : that.id == null;
  }

  @Override
  public String toString() {
    return "ExporterEntity{" + "id='" + id + '\'' + '}';
  }
}
