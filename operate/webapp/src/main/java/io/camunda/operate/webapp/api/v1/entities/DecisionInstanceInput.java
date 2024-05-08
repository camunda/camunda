/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionInstanceInput {

  private String id;
  private String name;
  private String value;

  public String getId() {
    return id;
  }

  public DecisionInstanceInput setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionInstanceInput setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DecisionInstanceInput setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceInput that = (DecisionInstanceInput) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value);
  }

  @Override
  public String toString() {
    return "DecisionInstanceInput{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + '}';
  }
}
