/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@EqualsAndHashCode
public class MapResultEntryDto<T extends Comparable> {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Getter @Setter private T value;
  @Setter private String label;
  // @formatter:on

  protected MapResultEntryDto() {
  }

  public MapResultEntryDto(@NonNull final String key, final T value) {
    this.key = key;
    this.value = value;
  }

  public MapResultEntryDto(@NonNull final String key, final T value, String label) {
    this.key = key;
    this.value = value;
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
