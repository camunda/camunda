/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Objects;

public class ProcessResponse {

  @Schema(description = "The unique identifier of the process")
  private String id;

  @Schema(description = "The name of the process")
  private String name;

  @Schema(description = "The BPMN process ID")
  private String bpmnProcessId;

  @Schema(
      description =
          "Array of values to be copied into `ProcessSearchRequest` to request for next or previous page of processes")
  private String[] sortValues;

  @Schema(description = "The version of the process")
  private Integer version;

  @Schema(description = "The ID of the form associated with the start event. Null if not set.")
  private String startEventFormId = null;

  @Schema(description = "The tenant ID associated with the process")
  private String tenantId;

  public String getId() {
    return id;
  }

  public ProcessResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessResponse setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getStartEventFormId() {
    return startEventFormId;
  }

  public ProcessResponse setStartEventFormId(String startEventFormId) {
    this.startEventFormId = startEventFormId;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ProcessResponse setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessResponse setVersion(Integer version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
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
    final ProcessResponse that = (ProcessResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(version, that.version)
        && Objects.equals(startEventFormId, that.startEventFormId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, bpmnProcessId, version, startEventFormId, tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return "ProcessResponse{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", sortValues="
        + Arrays.toString(sortValues)
        + ", version="
        + version
        + ", startEventFormId='"
        + startEventFormId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }

  public static ProcessResponse fromProcessEntity(ProcessEntity process, String startEventFormId) {
    return new ProcessResponse()
        .setId(process.getId())
        .setName(process.getName())
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setStartEventFormId(startEventFormId)
        .setTenantId(process.getTenantId());
  }
}
