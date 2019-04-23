/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

public class MapResultEntryDto<T> {

  @Getter @Setter private String key;
  @Getter @Setter private T value;
  @Setter private String label;

  protected MapResultEntryDto() {
  }

  public MapResultEntryDto(final String key, final T value) {
    this.key = key;
    this.value = value;
  }

  public Optional<String> getLabel() {
    return Optional.ofNullable(label);
  }
}
