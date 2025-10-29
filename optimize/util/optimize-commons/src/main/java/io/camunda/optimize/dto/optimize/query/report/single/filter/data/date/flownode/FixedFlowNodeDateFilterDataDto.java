/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode;

import static io.camunda.optimize.util.SuppressionConstants.UNUSED;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class FixedFlowNodeDateFilterDataDto extends FlowNodeDateFilterDataDto<OffsetDateTime> {

  @SuppressWarnings(UNUSED)
  protected FixedFlowNodeDateFilterDataDto() {
    this(null, null, null);
  }

  public FixedFlowNodeDateFilterDataDto(
      final List<String> flowNodeIds, final OffsetDateTime start, final OffsetDateTime end) {
    super(flowNodeIds, DateFilterType.FIXED, start, end);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FixedFlowNodeDateFilterDataDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), super.hashCode());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }
}
