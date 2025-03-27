/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.webapps.schema.entities.ProcessEntity;

public class ProcessDTO {

  private String id;

  private String name;

  private String processDefinitionId;

  private String[] sortValues;

  private boolean startedByForm;

  private String formKey;

  private String formId;

  private Boolean isFormEmbedded;

  private Integer version;

  public static ProcessDTO createFrom(final ProcessEntity processEntity) {
    return createFrom(processEntity, null);
  }

  public static ProcessDTO createFrom(
      final ProcessEntity processEntity, final Object[] sortValues) {
    final ProcessDTO processDTO =
        new ProcessDTO()
            .setId(processEntity.getId())
            .setName(processEntity.getName())
            .setProcessDefinitionId(processEntity.getBpmnProcessId())
            .setVersion(processEntity.getVersion())
            .setStartedByForm(processEntity.getIsPublic())
            .setFormKey(processEntity.getFormKey())
            .setFormId(processEntity.getFormId())
            .setFormEmbedded(processEntity.getIsFormEmbedded());

    if (sortValues != null) {
      processDTO.setSortValues(CollectionUtil.toArrayOfStrings(sortValues));
    }
    return processDTO;
  }

  public String getId() {
    return id;
  }

  public ProcessDTO setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDTO setName(final String name) {
    this.name = name;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ProcessDTO setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessDTO setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessDTO setVersion(final Integer version) {
    this.version = version;
    return this;
  }

  public boolean isStartedByForm() {
    return startedByForm;
  }

  public ProcessDTO setStartedByForm(final boolean startedByForm) {
    this.startedByForm = startedByForm;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public ProcessDTO setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public Boolean getFormEmbedded() {
    return isFormEmbedded;
  }

  public ProcessDTO setFormEmbedded(final Boolean formEmbedded) {
    isFormEmbedded = formEmbedded;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public ProcessDTO setFormId(final String formId) {
    this.formId = formId;
    return this;
  }
}
