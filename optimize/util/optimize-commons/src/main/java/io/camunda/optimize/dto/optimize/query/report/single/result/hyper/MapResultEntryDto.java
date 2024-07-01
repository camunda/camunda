/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result.hyper;

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
public class MapResultEntryDto {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Getter @Setter private Double value;
  @Setter private String label;

  // @formatter:on

  public MapResultEntryDto(@NonNull final String key, final Double value) {
    this.key = key;
    this.value = value;
  }

  public MapResultEntryDto(@NonNull final String key, final Double value, String label) {
    this.key = key;
    this.value = value;
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
