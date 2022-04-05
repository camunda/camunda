/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The request to get several lists of flow node instances.
 */
public class FlowNodeInstanceRequestDto {

  private List<FlowNodeInstanceQueryDto> queries;

  public FlowNodeInstanceRequestDto() {
  }

  public FlowNodeInstanceRequestDto(
      final List<FlowNodeInstanceQueryDto> queries) {
    this.queries = queries;
  }

  public FlowNodeInstanceRequestDto(FlowNodeInstanceQueryDto... queries) {
    this.queries = Arrays.asList(queries);
  }

  public List<FlowNodeInstanceQueryDto> getQueries() {
    return queries;
  }

  public FlowNodeInstanceRequestDto setQueries(
      final List<FlowNodeInstanceQueryDto> queries) {
    this.queries = queries;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceRequestDto that = (FlowNodeInstanceRequestDto) o;
    return Objects.equals(queries, that.queries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(queries);
  }
}
