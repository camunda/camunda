/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.util.Objects;

public class FormEntity extends TasklistEntity<FormEntity> {

  private String bpmnId;
  private String processDefinitionId;
  private String schema;

  public FormEntity() {}

  public FormEntity(String processDefinitionId, String bpmnId, String schema) {
    setId(createId(processDefinitionId, bpmnId));
    this.bpmnId = bpmnId;
    this.processDefinitionId = processDefinitionId;
    this.schema = schema;
  }

  public String getSchema() {
    return schema;
  }

  public FormEntity setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public String getBpmnId() {
    return bpmnId;
  }

  public FormEntity setBpmnId(final String bpmnId) {
    this.bpmnId = bpmnId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public FormEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public static String createId(String processId, String formKey) {
    return String.format("%s_%s", processId, formKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnId, processDefinitionId, schema);
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
    return Objects.equals(bpmnId, that.bpmnId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(schema, that.schema);
  }

  @Override
  public String toString() {
    return "FormEntity{"
        + "formKey='"
        + bpmnId
        + '\''
        + ", processId='"
        + processDefinitionId
        + '\''
        + ", schema='"
        + schema
        + '\''
        + '}';
  }
}
