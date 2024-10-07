/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import java.util.Objects;

/**
 * Represents an entity that can be written to ElasticSearch or OpenSearch
 *
 * @param <T>
 */
public abstract class AbstractExporterEntity<T extends AbstractExporterEntity<T>>
    implements ExporterEntity<T> {

  public static final String DEFAULT_TENANT_ID = "<default>";

  private String id;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public T setId(final String id) {
    this.id = id;
    return (T) this;
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
    final AbstractExporterEntity<?> that = (AbstractExporterEntity<?>) o;
    return Objects.equals(id, that.id);
  }
}
