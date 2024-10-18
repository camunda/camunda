/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.Optional;

public class FindingsDto {

  private Finding lowerOutlier;
  private Finding higherOutlier;
  private Double lowerOutlierHeat = 0.0D;
  private Double higherOutlierHeat = 0.0D;
  private Double heat = 0.0D;
  private Long totalCount;

  public FindingsDto() {}

  public Optional<Finding> getLowerOutlier() {
    return Optional.ofNullable(lowerOutlier);
  }

  public void setLowerOutlier(final Finding lowerOutlier) {
    this.lowerOutlier = lowerOutlier;
  }

  public Optional<Finding> getHigherOutlier() {
    return Optional.ofNullable(higherOutlier);
  }

  public void setHigherOutlier(final Finding higherOutlier) {
    this.higherOutlier = higherOutlier;
  }

  public void setLowerOutlier(
      final Long boundValue, final Double percentile, final Double relation, final Long count) {
    lowerOutlier = new Finding(boundValue, percentile, relation, count);
  }

  public void setHigherOutlier(
      final Long boundValue, final Double percentile, final Double relation, final Long count) {
    higherOutlier = new Finding(boundValue, percentile, relation, count);
  }

  public Long getOutlierCount() {
    return Optional.ofNullable(lowerOutlier).map(Finding::getCount).orElse(0L)
        + Optional.ofNullable(higherOutlier).map(Finding::getCount).orElse(0L);
  }

  public Double getLowerOutlierHeat() {
    return lowerOutlierHeat;
  }

  public void setLowerOutlierHeat(final Double lowerOutlierHeat) {
    this.lowerOutlierHeat = lowerOutlierHeat;
  }

  public Double getHigherOutlierHeat() {
    return higherOutlierHeat;
  }

  public void setHigherOutlierHeat(final Double higherOutlierHeat) {
    this.higherOutlierHeat = higherOutlierHeat;
  }

  public Double getHeat() {
    return heat;
  }

  public void setHeat(final Double heat) {
    this.heat = heat;
  }

  public Long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(final Long totalCount) {
    this.totalCount = totalCount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FindingsDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "FindingsDto(lowerOutlier="
        + getLowerOutlier()
        + ", higherOutlier="
        + getHigherOutlier()
        + ", lowerOutlierHeat="
        + getLowerOutlierHeat()
        + ", higherOutlierHeat="
        + getHigherOutlierHeat()
        + ", heat="
        + getHeat()
        + ", totalCount="
        + getTotalCount()
        + ")";
  }

  public class Finding {

    private Long boundValue;
    private Double percentile;
    private Double relation;
    private Long count = 0L;

    public Finding(
        final Long boundValue, final Double percentile, final Double relation, final Long count) {
      this.boundValue = boundValue;
      this.percentile = percentile;
      this.relation = relation;
      this.count = count;
    }

    public Finding() {}

    public Long getBoundValue() {
      return boundValue;
    }

    public void setBoundValue(final Long boundValue) {
      this.boundValue = boundValue;
    }

    public Double getPercentile() {
      return percentile;
    }

    public void setPercentile(final Double percentile) {
      this.percentile = percentile;
    }

    public Double getRelation() {
      return relation;
    }

    public void setRelation(final Double relation) {
      this.relation = relation;
    }

    public Long getCount() {
      return count;
    }

    public void setCount(final Long count) {
      this.count = count;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof Finding;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "FindingsDto.Finding(boundValue="
          + getBoundValue()
          + ", percentile="
          + getPercentile()
          + ", relation="
          + getRelation()
          + ", count="
          + getCount()
          + ")";
    }
  }
}
