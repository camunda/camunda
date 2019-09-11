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

import java.util.List;
import java.util.Optional;

@EqualsAndHashCode
public class HyperMapResultEntryDto<T extends Comparable> {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Getter @Setter private List<MapResultEntryDto<T>> value;
  @Setter private String label;
  // @formatter:on

  protected HyperMapResultEntryDto() {
  }

  public Optional<MapResultEntryDto<T>> getDataEntryForKey(final String key) {
    return value.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto<T>> value) {
    this.key = key;
    this.value = value;
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto<T>> value, String label) {
    this.key = key;
    this.value = value;
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
