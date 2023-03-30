/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import java.util.Objects;
import java.util.StringJoiner;

public class FormResponse {
  private String id;
  private String processDefinitionKey;
  private String schema;

  public String getId() {
    return id;
  }

  public FormResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FormResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormResponse setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormResponse that = (FormResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(schema, that.schema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionKey, schema);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", FormResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("schema='" + schema + "'")
        .toString();
  }

  public static FormResponse fromFormDTO(FormDTO form) {
    return new FormResponse()
        .setId(form.getId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setSchema(form.getSchema());
  }
}
