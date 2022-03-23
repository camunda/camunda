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

import java.util.List;

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

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value, String label) {
    this.key = key;
    setValue(value);
    this.label = label;
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }
}
