/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class ProcessResponse {
  private String id;
  private String name;
  private String processDefinitionKey;
  private String[] sortValues;
  private Integer version;

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

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
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
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, processDefinitionKey, version);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", ProcessResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("version=" + version)
        .toString();
  }

  public static ProcessResponse fromProcessDTO(ProcessDTO process) {
    return new ProcessResponse()
        .setId(process.getId())
        .setName(process.getName())
        .setProcessDefinitionKey(process.getProcessDefinitionId())
        .setSortValues(process.getSortValues())
        .setVersion(process.getVersion());
  }
}
