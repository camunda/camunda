/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.Objects;

public class BusinessValueOverviewDto implements OptimizeDto {

  private String tenantId;
  private String processDefinitionKey;
  private String processDefinitionName;
  private MetricRange metricRange;
  private OffsetDateTime lastComputedAt;
  private CycleTimeBlock cycleTime;
  private AutomationRateBlock automationRate;
  private boolean hasAnyTarget;
  private int targetsSet;
  private int targetsMet;

  public BusinessValueOverviewDto() {}

  public BusinessValueOverviewDto(
      final String tenantId,
      final String processDefinitionKey,
      final String processDefinitionName,
      final MetricRange metricRange,
      final OffsetDateTime lastComputedAt,
      final CycleTimeBlock cycleTime,
      final AutomationRateBlock automationRate,
      final boolean hasAnyTarget,
      final int targetsSet,
      final int targetsMet) {
    this.tenantId = tenantId;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionName = processDefinitionName;
    this.metricRange = metricRange;
    this.lastComputedAt = lastComputedAt;
    this.cycleTime = cycleTime;
    this.automationRate = automationRate;
    this.hasAnyTarget = hasAnyTarget;
    this.targetsSet = targetsSet;
    this.targetsMet = targetsMet;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public void setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public MetricRange getMetricRange() {
    return metricRange;
  }

  public void setMetricRange(final MetricRange metricRange) {
    this.metricRange = metricRange;
  }

  public OffsetDateTime getLastComputedAt() {
    return lastComputedAt;
  }

  public void setLastComputedAt(final OffsetDateTime lastComputedAt) {
    this.lastComputedAt = lastComputedAt;
  }

  public CycleTimeBlock getCycleTime() {
    return cycleTime;
  }

  public void setCycleTime(final CycleTimeBlock cycleTime) {
    this.cycleTime = cycleTime;
  }

  public AutomationRateBlock getAutomationRate() {
    return automationRate;
  }

  public void setAutomationRate(final AutomationRateBlock automationRate) {
    this.automationRate = automationRate;
  }

  public boolean isHasAnyTarget() {
    return hasAnyTarget;
  }

  public void setHasAnyTarget(final boolean hasAnyTarget) {
    this.hasAnyTarget = hasAnyTarget;
  }

  public int getTargetsSet() {
    return targetsSet;
  }

  public void setTargetsSet(final int targetsSet) {
    this.targetsSet = targetsSet;
  }

  public int getTargetsMet() {
    return targetsMet;
  }

  public void setTargetsMet(final int targetsMet) {
    this.targetsMet = targetsMet;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BusinessValueOverviewDto that = (BusinessValueOverviewDto) o;
    return hasAnyTarget == that.hasAnyTarget
        && targetsSet == that.targetsSet
        && targetsMet == that.targetsMet
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionName, that.processDefinitionName)
        && metricRange == that.metricRange
        && Objects.equals(lastComputedAt, that.lastComputedAt)
        && Objects.equals(cycleTime, that.cycleTime)
        && Objects.equals(automationRate, that.automationRate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        tenantId,
        processDefinitionKey,
        processDefinitionName,
        metricRange,
        lastComputedAt,
        cycleTime,
        automationRate,
        hasAnyTarget,
        targetsSet,
        targetsMet);
  }

  @Override
  public String toString() {
    return "BusinessValueOverviewDto(tenantId="
        + tenantId
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionName="
        + processDefinitionName
        + ", metricRange="
        + metricRange
        + ", lastComputedAt="
        + lastComputedAt
        + ", cycleTime="
        + cycleTime
        + ", automationRate="
        + automationRate
        + ", hasAnyTarget="
        + hasAnyTarget
        + ", targetsSet="
        + targetsSet
        + ", targetsMet="
        + targetsMet
        + ")";
  }

  public enum MetricRange {
    SEVEN_DAYS("7d"),
    THIRTY_DAYS("30d"),
    THREE_MONTHS("3m"),
    SIX_MONTHS("6m");

    private final String id;

    MetricRange(final String id) {
      this.id = id;
    }

    @JsonValue
    public String getId() {
      return id;
    }

    public static MetricRange fromId(final String id) {
      for (final MetricRange range : values()) {
        if (range.id.equals(id)) {
          return range;
        }
      }
      throw new IllegalArgumentException(
          "Unknown metricRange id [" + id + "]; must be one of: 7d, 30d, 3m, 6m");
    }
  }

  public static class CycleTimeBlock {

    private Long value;
    private Long target;
    private Boolean met;

    public CycleTimeBlock() {}

    public CycleTimeBlock(final Long value, final Long target, final Boolean met) {
      this.value = value;
      this.target = target;
      this.met = met;
    }

    public Long getValue() {
      return value;
    }

    public void setValue(final Long value) {
      this.value = value;
    }

    public Long getTarget() {
      return target;
    }

    public void setTarget(final Long target) {
      this.target = target;
    }

    public Boolean getMet() {
      return met;
    }

    public void setMet(final Boolean met) {
      this.met = met;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final CycleTimeBlock that = (CycleTimeBlock) o;
      return Objects.equals(value, that.value)
          && Objects.equals(target, that.target)
          && Objects.equals(met, that.met);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, target, met);
    }

    @Override
    public String toString() {
      return "CycleTimeBlock(value=" + value + ", target=" + target + ", met=" + met + ")";
    }
  }

  public static class AutomationRateBlock {

    private Double value;
    private Integer target;
    private Boolean met;

    public AutomationRateBlock() {}

    public AutomationRateBlock(final Double value, final Integer target, final Boolean met) {
      this.value = value;
      this.target = target;
      this.met = met;
    }

    public Double getValue() {
      return value;
    }

    public void setValue(final Double value) {
      this.value = value;
    }

    public Integer getTarget() {
      return target;
    }

    public void setTarget(final Integer target) {
      this.target = target;
    }

    public Boolean getMet() {
      return met;
    }

    public void setMet(final Boolean met) {
      this.met = met;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AutomationRateBlock that = (AutomationRateBlock) o;
      return Objects.equals(value, that.value)
          && Objects.equals(target, that.target)
          && Objects.equals(met, that.met);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, target, met);
    }

    @Override
    public String toString() {
      return "AutomationRateBlock(value=" + value + ", target=" + target + ", met=" + met + ")";
    }
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String tenantId = "tenantId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionName = "processDefinitionName";
    public static final String metricRange = "metricRange";
    public static final String lastComputedAt = "lastComputedAt";
    public static final String cycleTime = "cycleTime";
    public static final String automationRate = "automationRate";
    public static final String hasAnyTarget = "hasAnyTarget";
    public static final String targetsSet = "targetsSet";
    public static final String targetsMet = "targetsMet";
    public static final String value = "value";
    public static final String target = "target";
    public static final String met = "met";
  }
}
