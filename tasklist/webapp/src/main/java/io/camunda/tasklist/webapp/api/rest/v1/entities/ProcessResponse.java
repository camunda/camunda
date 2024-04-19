/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  @Schema(description = "The xml that represents the BPMN for the process")
  private String bpmnXml;

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

  public String getBpmnXml() {
    return bpmnXml;
  }

  public ProcessResponse setBpmnXml(final String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public static ProcessResponse fromProcessEntity(ProcessEntity process, String startEventFormId) {
    return new ProcessResponse()
        .setId(process.getId())
        .setName(process.getName())
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setStartEventFormId(startEventFormId)
        .setTenantId(process.getTenantId())
        .setBpmnXml(process.getBpmnXml());
  }

  public static ProcessResponse fromProcessEntityWithoutBpmnXml(ProcessEntity process, String startEventFormId) {
    process.setBpmnXml(null);
    return fromProcessEntity(process, startEventFormId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessResponse that = (ProcessResponse) o;
    return Objects.equals(id, that.id) && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.deepEquals(sortValues, that.sortValues) && Objects.equals(
        version, that.version) && Objects.equals(startEventFormId, that.startEventFormId)
        && Objects.equals(tenantId, that.tenantId) && Objects.equals(bpmnXml,
        that.bpmnXml);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, bpmnProcessId, Arrays.hashCode(sortValues), version,
        startEventFormId, tenantId, bpmnXml);
  }
}
