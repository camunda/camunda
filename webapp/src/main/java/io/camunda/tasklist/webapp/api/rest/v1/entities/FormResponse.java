/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import java.util.Objects;
import java.util.StringJoiner;

public class FormResponse {
  private String id;
  private String processDefinitionKey;
  private String title;
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getTitle() {
    return title;
  }

  public FormResponse setTitle(String processName) {
    this.title = processName;
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
  public String toString() {
    return new StringJoiner(", ", FormResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("title='" + title + "'")
        .add("schema='" + schema + "'")
        .toString();
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
        && Objects.equals(title, that.title)
        && Objects.equals(schema, that.schema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionKey, title, schema);
  }

  public static FormResponse fromFormDTO(FormDTO form) {
    return new FormResponse()
        .setId(form.getId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setSchema(form.getSchema());
  }

  public static FormResponse fromFormDTO(FormDTO form, ProcessDTO processDTO) {
    return new FormResponse()
        .setId(form.getId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setTitle(
            processDTO.getName() != null
                ? processDTO.getName()
                : processDTO.getProcessDefinitionId())
        .setSchema(form.getSchema());
  }
}
