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
    final int PRIME = 59;
    int result = 1;
    final Object $lowerOutlier = getLowerOutlier();
    result = result * PRIME + ($lowerOutlier == null ? 43 : $lowerOutlier.hashCode());
    final Object $higherOutlier = getHigherOutlier();
    result = result * PRIME + ($higherOutlier == null ? 43 : $higherOutlier.hashCode());
    final Object $lowerOutlierHeat = getLowerOutlierHeat();
    result = result * PRIME + ($lowerOutlierHeat == null ? 43 : $lowerOutlierHeat.hashCode());
    final Object $higherOutlierHeat = getHigherOutlierHeat();
    result = result * PRIME + ($higherOutlierHeat == null ? 43 : $higherOutlierHeat.hashCode());
    final Object $heat = getHeat();
    result = result * PRIME + ($heat == null ? 43 : $heat.hashCode());
    final Object $totalCount = getTotalCount();
    result = result * PRIME + ($totalCount == null ? 43 : $totalCount.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FindingsDto)) {
      return false;
    }
    final FindingsDto other = (FindingsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$lowerOutlier = getLowerOutlier();
    final Object other$lowerOutlier = other.getLowerOutlier();
    if (this$lowerOutlier == null
        ? other$lowerOutlier != null
        : !this$lowerOutlier.equals(other$lowerOutlier)) {
      return false;
    }
    final Object this$higherOutlier = getHigherOutlier();
    final Object other$higherOutlier = other.getHigherOutlier();
    if (this$higherOutlier == null
        ? other$higherOutlier != null
        : !this$higherOutlier.equals(other$higherOutlier)) {
      return false;
    }
    final Object this$lowerOutlierHeat = getLowerOutlierHeat();
    final Object other$lowerOutlierHeat = other.getLowerOutlierHeat();
    if (this$lowerOutlierHeat == null
        ? other$lowerOutlierHeat != null
        : !this$lowerOutlierHeat.equals(other$lowerOutlierHeat)) {
      return false;
    }
    final Object this$higherOutlierHeat = getHigherOutlierHeat();
    final Object other$higherOutlierHeat = other.getHigherOutlierHeat();
    if (this$higherOutlierHeat == null
        ? other$higherOutlierHeat != null
        : !this$higherOutlierHeat.equals(other$higherOutlierHeat)) {
      return false;
    }
    final Object this$heat = getHeat();
    final Object other$heat = other.getHeat();
    if (this$heat == null ? other$heat != null : !this$heat.equals(other$heat)) {
      return false;
    }
    final Object this$totalCount = getTotalCount();
    final Object other$totalCount = other.getTotalCount();
    if (this$totalCount == null
        ? other$totalCount != null
        : !this$totalCount.equals(other$totalCount)) {
      return false;
    }
    return true;
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
      final int PRIME = 59;
      int result = 1;
      final Object $boundValue = getBoundValue();
      result = result * PRIME + ($boundValue == null ? 43 : $boundValue.hashCode());
      final Object $percentile = getPercentile();
      result = result * PRIME + ($percentile == null ? 43 : $percentile.hashCode());
      final Object $relation = getRelation();
      result = result * PRIME + ($relation == null ? 43 : $relation.hashCode());
      final Object $count = getCount();
      result = result * PRIME + ($count == null ? 43 : $count.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof Finding)) {
        return false;
      }
      final Finding other = (Finding) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$boundValue = getBoundValue();
      final Object other$boundValue = other.getBoundValue();
      if (this$boundValue == null
          ? other$boundValue != null
          : !this$boundValue.equals(other$boundValue)) {
        return false;
      }
      final Object this$percentile = getPercentile();
      final Object other$percentile = other.getPercentile();
      if (this$percentile == null
          ? other$percentile != null
          : !this$percentile.equals(other$percentile)) {
        return false;
      }
      final Object this$relation = getRelation();
      final Object other$relation = other.getRelation();
      if (this$relation == null ? other$relation != null : !this$relation.equals(other$relation)) {
        return false;
      }
      final Object this$count = getCount();
      final Object other$count = other.getCount();
      if (this$count == null ? other$count != null : !this$count.equals(other$count)) {
        return false;
      }
      return true;
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
