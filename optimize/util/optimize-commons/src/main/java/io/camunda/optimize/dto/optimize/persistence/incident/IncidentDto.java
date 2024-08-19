/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;

public class IncidentDto implements Serializable, OptimizeDto {

  protected String id;
  protected OffsetDateTime createTime;
  protected OffsetDateTime endTime;
  protected Long durationInMs;
  protected IncidentType incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String incidentMessage;
  protected IncidentStatus incidentStatus;
  @JsonIgnore private String processInstanceId;
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;
  @JsonIgnore private String engineAlias;

  public IncidentDto(
      final String processInstanceId,
      final String definitionKey,
      final String definitionVersion,
      final String tenantId,
      final String engineAlias,
      final String id,
      final OffsetDateTime createTime,
      final OffsetDateTime endTime,
      final Long durationInMs,
      final IncidentType incidentType,
      final String activityId,
      final String failedActivityId,
      final String incidentMessage,
      final IncidentStatus incidentStatus) {
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
    this.engineAlias = engineAlias;
    this.id = id;
    this.createTime = createTime;
    this.endTime = endTime;
    this.durationInMs = durationInMs;
    this.incidentType = incidentType;
    this.activityId = activityId;
    this.failedActivityId = failedActivityId;
    this.incidentMessage = incidentMessage;
    this.incidentStatus = incidentStatus;
  }

  public IncidentDto() {}

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  @JsonIgnore
  public IncidentDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public IncidentDto setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
    return this;
  }

  public String getDefinitionVersion() {
    return definitionVersion;
  }

  public IncidentDto setDefinitionVersion(final String definitionVersion) {
    this.definitionVersion = definitionVersion;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public IncidentDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  @JsonIgnore
  public IncidentDto setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
    return this;
  }

  public String getId() {
    return id;
  }

  public IncidentDto setId(final String id) {
    this.id = id;
    return this;
  }

  public OffsetDateTime getCreateTime() {
    return createTime;
  }

  public IncidentDto setCreateTime(final OffsetDateTime createTime) {
    this.createTime = createTime;
    return this;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public IncidentDto setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
    return this;
  }

  public Long getDurationInMs() {
    return durationInMs;
  }

  public IncidentDto setDurationInMs(final Long durationInMs) {
    this.durationInMs = durationInMs;
    return this;
  }

  public IncidentType getIncidentType() {
    return incidentType;
  }

  public IncidentDto setIncidentType(final IncidentType incidentType) {
    this.incidentType = incidentType;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public IncidentDto setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  public String getFailedActivityId() {
    return failedActivityId;
  }

  public IncidentDto setFailedActivityId(final String failedActivityId) {
    this.failedActivityId = failedActivityId;
    return this;
  }

  public String getIncidentMessage() {
    return incidentMessage;
  }

  public IncidentDto setIncidentMessage(final String incidentMessage) {
    this.incidentMessage = incidentMessage;
    return this;
  }

  public IncidentStatus getIncidentStatus() {
    return incidentStatus;
  }

  public IncidentDto setIncidentStatus(final IncidentStatus incidentStatus) {
    this.incidentStatus = incidentStatus;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IncidentDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $definitionKey = getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionVersion = getDefinitionVersion();
    result = result * PRIME + ($definitionVersion == null ? 43 : $definitionVersion.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $engineAlias = getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $createTime = getCreateTime();
    result = result * PRIME + ($createTime == null ? 43 : $createTime.hashCode());
    final Object $endTime = getEndTime();
    result = result * PRIME + ($endTime == null ? 43 : $endTime.hashCode());
    final Object $durationInMs = getDurationInMs();
    result = result * PRIME + ($durationInMs == null ? 43 : $durationInMs.hashCode());
    final Object $incidentType = getIncidentType();
    result = result * PRIME + ($incidentType == null ? 43 : $incidentType.hashCode());
    final Object $activityId = getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $failedActivityId = getFailedActivityId();
    result = result * PRIME + ($failedActivityId == null ? 43 : $failedActivityId.hashCode());
    final Object $incidentMessage = getIncidentMessage();
    result = result * PRIME + ($incidentMessage == null ? 43 : $incidentMessage.hashCode());
    final Object $incidentStatus = getIncidentStatus();
    result = result * PRIME + ($incidentStatus == null ? 43 : $incidentStatus.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IncidentDto)) {
      return false;
    }
    final IncidentDto other = (IncidentDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$definitionKey = getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionVersion = getDefinitionVersion();
    final Object other$definitionVersion = other.getDefinitionVersion();
    if (this$definitionVersion == null
        ? other$definitionVersion != null
        : !this$definitionVersion.equals(other$definitionVersion)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$engineAlias = getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$createTime = getCreateTime();
    final Object other$createTime = other.getCreateTime();
    if (this$createTime == null
        ? other$createTime != null
        : !this$createTime.equals(other$createTime)) {
      return false;
    }
    final Object this$endTime = getEndTime();
    final Object other$endTime = other.getEndTime();
    if (this$endTime == null ? other$endTime != null : !this$endTime.equals(other$endTime)) {
      return false;
    }
    final Object this$durationInMs = getDurationInMs();
    final Object other$durationInMs = other.getDurationInMs();
    if (this$durationInMs == null
        ? other$durationInMs != null
        : !this$durationInMs.equals(other$durationInMs)) {
      return false;
    }
    final Object this$incidentType = getIncidentType();
    final Object other$incidentType = other.getIncidentType();
    if (this$incidentType == null
        ? other$incidentType != null
        : !this$incidentType.equals(other$incidentType)) {
      return false;
    }
    final Object this$activityId = getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$failedActivityId = getFailedActivityId();
    final Object other$failedActivityId = other.getFailedActivityId();
    if (this$failedActivityId == null
        ? other$failedActivityId != null
        : !this$failedActivityId.equals(other$failedActivityId)) {
      return false;
    }
    final Object this$incidentMessage = getIncidentMessage();
    final Object other$incidentMessage = other.getIncidentMessage();
    if (this$incidentMessage == null
        ? other$incidentMessage != null
        : !this$incidentMessage.equals(other$incidentMessage)) {
      return false;
    }
    final Object this$incidentStatus = getIncidentStatus();
    final Object other$incidentStatus = other.getIncidentStatus();
    if (this$incidentStatus == null
        ? other$incidentStatus != null
        : !this$incidentStatus.equals(other$incidentStatus)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IncidentDto(processInstanceId="
        + getProcessInstanceId()
        + ", definitionKey="
        + getDefinitionKey()
        + ", definitionVersion="
        + getDefinitionVersion()
        + ", tenantId="
        + getTenantId()
        + ", engineAlias="
        + getEngineAlias()
        + ", id="
        + getId()
        + ", createTime="
        + getCreateTime()
        + ", endTime="
        + getEndTime()
        + ", durationInMs="
        + getDurationInMs()
        + ", incidentType="
        + getIncidentType()
        + ", activityId="
        + getActivityId()
        + ", failedActivityId="
        + getFailedActivityId()
        + ", incidentMessage="
        + getIncidentMessage()
        + ", incidentStatus="
        + getIncidentStatus()
        + ")";
  }

  public static IncidentDtoBuilder builder() {
    return new IncidentDtoBuilder();
  }

  public static final class Fields {

    public static final String processInstanceId = "processInstanceId";
    public static final String definitionKey = "definitionKey";
    public static final String definitionVersion = "definitionVersion";
    public static final String tenantId = "tenantId";
    public static final String engineAlias = "engineAlias";
    public static final String id = "id";
    public static final String createTime = "createTime";
    public static final String endTime = "endTime";
    public static final String durationInMs = "durationInMs";
    public static final String incidentType = "incidentType";
    public static final String activityId = "activityId";
    public static final String failedActivityId = "failedActivityId";
    public static final String incidentMessage = "incidentMessage";
    public static final String incidentStatus = "incidentStatus";
  }

  public static class IncidentDtoBuilder {

    private String processInstanceId;
    private String definitionKey;
    private String definitionVersion;
    private String tenantId;
    private String engineAlias;
    private String id;
    private OffsetDateTime createTime;
    private OffsetDateTime endTime;
    private Long durationInMs;
    private IncidentType incidentType;
    private String activityId;
    private String failedActivityId;
    private String incidentMessage;
    private IncidentStatus incidentStatus;

    IncidentDtoBuilder() {}

    @JsonIgnore
    public IncidentDtoBuilder processInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public IncidentDtoBuilder definitionKey(final String definitionKey) {
      this.definitionKey = definitionKey;
      return this;
    }

    public IncidentDtoBuilder definitionVersion(final String definitionVersion) {
      this.definitionVersion = definitionVersion;
      return this;
    }

    public IncidentDtoBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @JsonIgnore
    public IncidentDtoBuilder engineAlias(final String engineAlias) {
      this.engineAlias = engineAlias;
      return this;
    }

    public IncidentDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public IncidentDtoBuilder createTime(final OffsetDateTime createTime) {
      this.createTime = createTime;
      return this;
    }

    public IncidentDtoBuilder endTime(final OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public IncidentDtoBuilder durationInMs(final Long durationInMs) {
      this.durationInMs = durationInMs;
      return this;
    }

    public IncidentDtoBuilder incidentType(final IncidentType incidentType) {
      this.incidentType = incidentType;
      return this;
    }

    public IncidentDtoBuilder activityId(final String activityId) {
      this.activityId = activityId;
      return this;
    }

    public IncidentDtoBuilder failedActivityId(final String failedActivityId) {
      this.failedActivityId = failedActivityId;
      return this;
    }

    public IncidentDtoBuilder incidentMessage(final String incidentMessage) {
      this.incidentMessage = incidentMessage;
      return this;
    }

    public IncidentDtoBuilder incidentStatus(final IncidentStatus incidentStatus) {
      this.incidentStatus = incidentStatus;
      return this;
    }

    public IncidentDto build() {
      return new IncidentDto(
          processInstanceId,
          definitionKey,
          definitionVersion,
          tenantId,
          engineAlias,
          id,
          createTime,
          endTime,
          durationInMs,
          incidentType,
          activityId,
          failedActivityId,
          incidentMessage,
          incidentStatus);
    }

    @Override
    public String toString() {
      return "IncidentDto.IncidentDtoBuilder(processInstanceId="
          + processInstanceId
          + ", definitionKey="
          + definitionKey
          + ", definitionVersion="
          + definitionVersion
          + ", tenantId="
          + tenantId
          + ", engineAlias="
          + engineAlias
          + ", id="
          + id
          + ", createTime="
          + createTime
          + ", endTime="
          + endTime
          + ", durationInMs="
          + durationInMs
          + ", incidentType="
          + incidentType
          + ", activityId="
          + activityId
          + ", failedActivityId="
          + failedActivityId
          + ", incidentMessage="
          + incidentMessage
          + ", incidentStatus="
          + incidentStatus
          + ")";
    }
  }
}
