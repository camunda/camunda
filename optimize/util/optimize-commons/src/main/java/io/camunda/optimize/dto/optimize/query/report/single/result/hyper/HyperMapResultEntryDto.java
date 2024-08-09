/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HyperMapResultEntryDto {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Getter @Setter private List<MapResultEntryDto> value;
  @Setter private String label;

  // @formatter:on

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value) {
    this.key = key;
    this.value = value;
  }

  public HyperMapResultEntryDto(
      @NonNull final String key, final List<MapResultEntryDto> value, String label) {
    this.key = key;
    setValue(value);
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
