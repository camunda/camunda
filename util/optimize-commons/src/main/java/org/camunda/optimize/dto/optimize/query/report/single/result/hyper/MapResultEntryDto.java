/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

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
