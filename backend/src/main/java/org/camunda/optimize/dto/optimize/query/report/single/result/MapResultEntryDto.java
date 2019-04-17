/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import java.util.Optional;

public class MapResultEntryDto<T> {

  private String key;
  private T value;

  private String label;

  protected MapResultEntryDto() {
  }

  public MapResultEntryDto(final String key, final T value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public T getValue() {
    return value;
  }

  public void setValue(final T value) {
    this.value = value;
  }

  public Optional<String> getLabel() {
    return Optional.ofNullable(label);
  }

  public void setLabel(final String label) {
    this.label = label;
  }
}
