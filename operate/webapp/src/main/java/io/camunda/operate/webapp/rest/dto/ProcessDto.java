/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.webapps.schema.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(name = "Process object")
public class ProcessDto implements CreatableFromEntity<ProcessDto, ProcessEntity> {

  @Schema(
      description =
          "Unique id of the process, must be used when filtering instances by process ids.")
  private String id;

  private String name;
  private int version;
  private String bpmnProcessId;
  private String versionTag;

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

  public String getVersionTag() {
    return versionTag;
  }

  public ProcessDto setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  @Override
  public ProcessDto fillFrom(final ProcessEntity processEntity) {
    setId(processEntity.getId())
        .setBpmnProcessId(processEntity.getBpmnProcessId())
        .setName(processEntity.getName())
        .setVersion(processEntity.getVersion())
        .setVersionTag(processEntity.getVersionTag());
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, version, bpmnProcessId, versionTag);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDto that = (ProcessDto) o;
    return version == that.version
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(versionTag, that.versionTag);
  }
}
