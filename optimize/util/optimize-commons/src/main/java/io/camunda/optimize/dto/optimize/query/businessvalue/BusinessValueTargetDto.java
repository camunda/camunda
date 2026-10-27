/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import java.time.OffsetDateTime;
import java.util.Objects;

public class BusinessValueTargetDto implements OptimizeDto {

  private String processDefinitionKey;
  private String tenantId;
  private Long cycleTimeTargetMillis;
  private TargetValueUnit cycleTimeTargetUnit;
  private Integer automationRateTargetPct;
  private OffsetDateTime updatedAt;
  private String updatedBy;

  public BusinessValueTargetDto() {}

  public BusinessValueTargetDto(
      final String processDefinitionKey,
      final String tenantId,
      final Long cycleTimeTargetMillis,
      final TargetValueUnit cycleTimeTargetUnit,
      final Integer automationRateTargetPct,
      final OffsetDateTime updatedAt,
      final String updatedBy) {
    this.processDefinitionKey = processDefinitionKey;
    this.tenantId = tenantId;
    this.cycleTimeTargetMillis = cycleTimeTargetMillis;
    this.cycleTimeTargetUnit = cycleTimeTargetUnit;
    this.automationRateTargetPct = automationRateTargetPct;
    this.updatedAt = updatedAt;
    this.updatedBy = updatedBy;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public Long getCycleTimeTargetMillis() {
    return cycleTimeTargetMillis;
  }

  public void setCycleTimeTargetMillis(final Long cycleTimeTargetMillis) {
    this.cycleTimeTargetMillis = cycleTimeTargetMillis;
  }

  public TargetValueUnit getCycleTimeTargetUnit() {
    return cycleTimeTargetUnit;
  }

  public void setCycleTimeTargetUnit(final TargetValueUnit cycleTimeTargetUnit) {
    this.cycleTimeTargetUnit = cycleTimeTargetUnit;
  }

  public Integer getAutomationRateTargetPct() {
    return automationRateTargetPct;
  }

  public void setAutomationRateTargetPct(final Integer automationRateTargetPct) {
    this.automationRateTargetPct = automationRateTargetPct;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(final OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(final String updatedBy) {
    this.updatedBy = updatedBy;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BusinessValueTargetDto that = (BusinessValueTargetDto) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(cycleTimeTargetMillis, that.cycleTimeTargetMillis)
        && cycleTimeTargetUnit == that.cycleTimeTargetUnit
        && Objects.equals(automationRateTargetPct, that.automationRateTargetPct)
        && Objects.equals(updatedAt, that.updatedAt)
        && Objects.equals(updatedBy, that.updatedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processDefinitionKey,
        tenantId,
        cycleTimeTargetMillis,
        cycleTimeTargetUnit,
        automationRateTargetPct,
        updatedAt,
        updatedBy);
  }

  @Override
  public String toString() {
    return "BusinessValueTargetDto(processDefinitionKey="
        + processDefinitionKey
        + ", tenantId="
        + tenantId
        + ", cycleTimeTargetMillis="
        + cycleTimeTargetMillis
        + ", cycleTimeTargetUnit="
        + cycleTimeTargetUnit
        + ", automationRateTargetPct="
        + automationRateTargetPct
        + ", updatedAt="
        + updatedAt
        + ", updatedBy="
        + updatedBy
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String tenantId = "tenantId";
    public static final String cycleTimeTargetMillis = "cycleTimeTargetMillis";
    public static final String cycleTimeTargetUnit = "cycleTimeTargetUnit";
    public static final String automationRateTargetPct = "automationRateTargetPct";
    public static final String updatedAt = "updatedAt";
    public static final String updatedBy = "updatedBy";
  }
}
