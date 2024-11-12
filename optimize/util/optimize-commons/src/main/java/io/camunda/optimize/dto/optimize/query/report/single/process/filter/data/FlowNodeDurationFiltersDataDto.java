/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.HashMap;
import java.util.Map;

public class FlowNodeDurationFiltersDataDto extends HashMap<String, DurationFilterDataDto>
    implements FilterDataDto {

  public FlowNodeDurationFiltersDataDto() {
    super();
  }

  public FlowNodeDurationFiltersDataDto(final Map<String, DurationFilterDataDto> map) {
    super(map);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public String toString() {
    return "FlowNodeDurationFiltersDataDto()";
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeDurationFiltersDataDto;
  }
}
