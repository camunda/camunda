/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.util.CollectionUtil;

public class ProcessDTO {

  private String id;

  private String name;

  private String processDefinitionId;

  private String[] sortValues;

  private boolean startedByForm;

  private String formKey;

  private Integer version;

  public static ProcessDTO createFrom(ProcessEntity processEntity, ObjectMapper objectMapper) {
    return createFrom(processEntity, null, objectMapper);
  }

  public static ProcessDTO createFrom(
      ProcessEntity processEntity, Object[] sortValues, ObjectMapper objectMapper) {
    final ProcessDTO processDTO =
        new ProcessDTO()
            .setId(processEntity.getId())
            .setName(processEntity.getName())
            .setProcessDefinitionId(processEntity.getBpmnProcessId())
            .setVersion(processEntity.getVersion())
            .setStartedByForm(processEntity.isStartedByForm())
            .setFormKey(processEntity.getFormKey());

    if (sortValues != null) {
      processDTO.setSortValues(CollectionUtil.toArrayOfStrings(sortValues));
    }
    return processDTO;
  }

  public String getId() {
    return id;
  }

  public ProcessDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ProcessDTO setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessDTO setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessDTO setVersion(Integer version) {
    this.version = version;
    return this;
  }

  public boolean isStartedByForm() {
    return startedByForm;
  }

  public ProcessDTO setStartedByForm(boolean startedByForm) {
    this.startedByForm = startedByForm;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public ProcessDTO setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }
}
