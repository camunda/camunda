/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Process object")
public class ProcessDto implements CreatableFromEntity<ProcessDto, ProcessEntity> {

  @Schema(
      description =
          "Unique id of the process, must be used when filtering instances by process ids.")
  private String id;

  private String name;
  private int version;
  private String bpmnProcessId;

  public String getId() {
    return id;
  }

  public ProcessDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDto setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public ProcessDto setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessDto setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public ProcessDto fillFrom(final ProcessEntity processEntity) {
    this.setId(processEntity.getId())
        .setBpmnProcessId(processEntity.getBpmnProcessId())
        .setName(processEntity.getName())
        .setVersion(processEntity.getVersion());
    return this;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProcessDto that = (ProcessDto) o;

    if (version != that.version) {
      return false;
    }
    if (id != null ? !id.equals(that.id) : that.id != null) {
      return false;
    }
    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    return bpmnProcessId != null
        ? bpmnProcessId.equals(that.bpmnProcessId)
        : that.bpmnProcessId == null;
  }
}
