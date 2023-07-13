/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class ProcessResponse {
  private String id;
  private String name;
  private String bpmnProcessId;
  private String[] sortValues;
  private Integer version;
  private String startEventFormId = null;

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
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, bpmnProcessId, version);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", ProcessResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("bpmnProcessId='" + bpmnProcessId + "'")
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("version=" + version)
        .add("startEventFormId=" + startEventFormId)
        .toString();
  }

  public static ProcessResponse fromProcessDTO(ProcessDTO process, ProcessReader processReader) {
    return new ProcessResponse()
        .setId(process.getId())
        .setName(process.getName())
        .setBpmnProcessId(process.getProcessDefinitionId())
        .setSortValues(process.getSortValues())
        .setVersion(process.getVersion())
        .setStartEventFormId(processReader.getStartEventFormIdByBpmnProcess(process));
  }
}
