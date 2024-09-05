/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;

public class VariableEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String value;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String fullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isPreview;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String scopeKey;

  // Add getter and setters
  public String getId() {
    return id;
  }

  public VariableEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsPreview() {
    return isPreview;
  }

  public VariableEntity setIsPreview(final Boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  public String getScopeKey() {
    return scopeKey;
  }

  public VariableEntity setScopeKey(final String scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }
}
