/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.schema.indices.ProcessIndex;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessDefinition {

  // Used for index field search and sorting
  public static final String
      KEY = ProcessIndex.KEY,
      NAME = ProcessIndex.NAME,
      VERSION = ProcessIndex.VERSION,
      BPMN_PROCESS_ID = ProcessIndex.BPMN_PROCESS_ID;

  private Long key;
  private String name;
  private Integer version;
  private String bpmnProcessId;

  public Long getKey() {
    return key;
  }

  public ProcessDefinition setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDefinition setName(String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessDefinition setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessDefinition setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDefinition that = (ProcessDefinition) o;
    return Objects.equals(key, that.key) && Objects.equals(name, that.name)
        && Objects.equals(version, that.version) && Objects.equals(bpmnProcessId,
        that.bpmnProcessId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, name, version, bpmnProcessId);
  }

  @Override
  public String toString() {
    return "ProcessDefinition{" +
        "key=" + key +
        ", name='" + name + '\'' +
        ", version=" + version +
        ", bpmnProcessId='" + bpmnProcessId + '\'' +
        '}';
  }
}
