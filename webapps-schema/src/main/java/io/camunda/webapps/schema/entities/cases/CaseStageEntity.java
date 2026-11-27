/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.cases;

import java.util.List;
import java.util.Objects;

public final class CaseStageEntity {

  private String elementId;
  private String name;
  private boolean alternative;
  private CaseStageState state;
  private List<String> nextElementIds;

  public String getElementId() {
    return elementId;
  }

  public CaseStageEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getName() {
    return name;
  }

  public CaseStageEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public boolean isAlternative() {
    return alternative;
  }

  public CaseStageEntity setAlternative(final boolean alternative) {
    this.alternative = alternative;
    return this;
  }

  public CaseStageState getState() {
    return state;
  }

  public CaseStageEntity setState(final CaseStageState state) {
    this.state = state;
    return this;
  }

  public List<String> getNextElementIds() {
    return nextElementIds;
  }

  public CaseStageEntity setNextElementIds(final List<String> nextElementIds) {
    this.nextElementIds = nextElementIds;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementId, name, alternative, state, nextElementIds);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CaseStageEntity that = (CaseStageEntity) o;
    return alternative == that.alternative
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(name, that.name)
        && state == that.state
        && Objects.equals(nextElementIds, that.nextElementIds);
  }
}
