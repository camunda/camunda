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
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import java.util.List;

public class RelativeFlowNodeDateFilterDataDto
    extends FlowNodeDateFilterDataDto<RelativeDateFilterStartDto> {

  @SuppressWarnings(UNUSED)
  protected RelativeFlowNodeDateFilterDataDto() {
    this(null, null);
  }

  public RelativeFlowNodeDateFilterDataDto(
      final List<String> flowNodeIds, final RelativeDateFilterStartDto relativeDateFilterStartDto) {
    super(flowNodeIds, DateFilterType.RELATIVE, relativeDateFilterStartDto, null);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof RelativeFlowNodeDateFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RelativeFlowNodeDateFilterDataDto)) {
      return false;
    }
    final RelativeFlowNodeDateFilterDataDto other = (RelativeFlowNodeDateFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }
}
