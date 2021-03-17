/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import io.zeebe.tasklist.entities.FormEntity;
import java.util.Objects;

public class FormDTO {

  private String id;

  private String schema;

  public String getId() {
    return id;
  }

  public FormDTO setId(final String id) {
    this.id = id;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormDTO setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public static FormDTO createFrom(FormEntity formEntity) {
    return new FormDTO().setId(formEntity.getId()).setSchema(formEntity.getSchema());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, schema);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormDTO formDTO = (FormDTO) o;
    return Objects.equals(id, formDTO.id) && Objects.equals(schema, formDTO.schema);
  }
}
