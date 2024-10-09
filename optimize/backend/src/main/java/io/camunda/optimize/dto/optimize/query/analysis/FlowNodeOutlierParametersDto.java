/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

public class FlowNodeOutlierParametersDto extends ProcessDefinitionParametersDto {

  protected String flowNodeId;
  protected Long lowerOutlierBound;
  protected Long higherOutlierBound;

  public FlowNodeOutlierParametersDto() {}

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long getLowerOutlierBound() {
    return lowerOutlierBound;
  }

  public void setLowerOutlierBound(final Long lowerOutlierBound) {
    this.lowerOutlierBound = lowerOutlierBound;
  }

  public Long getHigherOutlierBound() {
    return higherOutlierBound;
  }

  public void setHigherOutlierBound(final Long higherOutlierBound) {
    this.higherOutlierBound = higherOutlierBound;
  }

  @Override
  public String toString() {
    return "FlowNodeOutlierParametersDto(flowNodeId="
        + getFlowNodeId()
        + ", lowerOutlierBound="
        + getLowerOutlierBound()
        + ", higherOutlierBound="
        + getHigherOutlierBound()
        + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeOutlierParametersDto)) {
      return false;
    }
    final FlowNodeOutlierParametersDto other = (FlowNodeOutlierParametersDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$flowNodeId = getFlowNodeId();
    final Object other$flowNodeId = other.getFlowNodeId();
    if (this$flowNodeId == null
        ? other$flowNodeId != null
        : !this$flowNodeId.equals(other$flowNodeId)) {
      return false;
    }
    final Object this$lowerOutlierBound = getLowerOutlierBound();
    final Object other$lowerOutlierBound = other.getLowerOutlierBound();
    if (this$lowerOutlierBound == null
        ? other$lowerOutlierBound != null
        : !this$lowerOutlierBound.equals(other$lowerOutlierBound)) {
      return false;
    }
    final Object this$higherOutlierBound = getHigherOutlierBound();
    final Object other$higherOutlierBound = other.getHigherOutlierBound();
    if (this$higherOutlierBound == null
        ? other$higherOutlierBound != null
        : !this$higherOutlierBound.equals(other$higherOutlierBound)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeOutlierParametersDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $flowNodeId = getFlowNodeId();
    result = result * PRIME + ($flowNodeId == null ? 43 : $flowNodeId.hashCode());
    final Object $lowerOutlierBound = getLowerOutlierBound();
    result = result * PRIME + ($lowerOutlierBound == null ? 43 : $lowerOutlierBound.hashCode());
    final Object $higherOutlierBound = getHigherOutlierBound();
    result = result * PRIME + ($higherOutlierBound == null ? 43 : $higherOutlierBound.hashCode());
    return result;
  }
}
