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
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeOutlierParametersDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }
}
