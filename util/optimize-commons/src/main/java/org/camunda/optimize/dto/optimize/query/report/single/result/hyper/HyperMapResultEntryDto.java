/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Optional;

@EqualsAndHashCode
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HyperMapResultEntryDto {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Getter @Setter private List<MapResultEntryDto> value;
  @Setter private String label;
  // @formatter:on

  public Optional<MapResultEntryDto> getDataEntryForKey(final String key) {
    return this.value.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value) {
    this.key = key;
    this.value = value;
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value, String label) {
    this.key = key;
    setValue(value);
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
