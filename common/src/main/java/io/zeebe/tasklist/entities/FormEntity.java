/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.util.Objects;

public class FormEntity extends TasklistEntity<FormEntity> {

  private String formKey;
  private String workflowId;
  private String schema;

  public FormEntity() {}

  public FormEntity(String workflowId, String formKey, String schema) {
    setId(createId(workflowId, formKey));
    this.formKey = formKey;
    this.workflowId = workflowId;
    this.schema = schema;
  }

  public String getSchema() {
    return schema;
  }

  public FormEntity setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public static String createId(String workflowId, String formKey) {
    return String.format("%s_%s", workflowId, formKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), formKey, workflowId, schema);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FormEntity that = (FormEntity) o;
    return Objects.equals(formKey, that.formKey)
        && Objects.equals(workflowId, that.workflowId)
        && Objects.equals(schema, that.schema);
  }

  @Override
  public String toString() {
    return "FormEntity{"
        + "formKey='"
        + formKey
        + '\''
        + ", workflowId='"
        + workflowId
        + '\''
        + ", schema='"
        + schema
        + '\''
        + '}';
  }
}
