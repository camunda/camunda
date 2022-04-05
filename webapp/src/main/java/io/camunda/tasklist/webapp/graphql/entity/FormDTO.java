/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.entities.FormEntity;
import java.util.Objects;

public class FormDTO {

  private String id;

  private String processDefinitionId;

  private String schema;

  public String getId() {
    return id;
  }

  public FormDTO setId(final String id) {
    this.id = id;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public FormDTO setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
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
    return new FormDTO()
        .setId(formEntity.getBpmnId())
        .setProcessDefinitionId(formEntity.getProcessDefinitionId())
        .setSchema(formEntity.getSchema());
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
    return Objects.equals(id, formDTO.id)
        && Objects.equals(processDefinitionId, formDTO.processDefinitionId)
        && Objects.equals(schema, formDTO.schema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionId, schema);
  }
}
