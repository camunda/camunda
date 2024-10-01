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
      String id,
      OffsetDateTime createTime,
      OffsetDateTime endTime,
      Long durationInMs,
      IncidentType incidentType,
      String activityId,
      String failedActivityId,
      String incidentMessage,
      IncidentStatus incidentStatus,
      String processInstanceId,
      String definitionKey,
      String definitionVersion,
      String tenantId,
      String engineAlias) {
    this.id = id;
    this.createTime = createTime;
    this.endTime = endTime;
    this.durationInMs = durationInMs;
    this.incidentType = incidentType;
    this.activityId = activityId;
    this.failedActivityId = failedActivityId;
    this.incidentMessage = incidentMessage;
    this.incidentStatus = incidentStatus;
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
    this.engineAlias = engineAlias;
  }

  public IncidentDto() {}

  public String getId() {
    return this.id;
  }

  public OffsetDateTime getCreateTime() {
    return this.createTime;
  }

  public OffsetDateTime getEndTime() {
    return this.endTime;
  }

  public Long getDurationInMs() {
    return this.durationInMs;
  }

  public IncidentType getIncidentType() {
    return this.incidentType;
  }

  public String getActivityId() {
    return this.activityId;
  }

  public String getFailedActivityId() {
    return this.failedActivityId;
  }

  public String getIncidentMessage() {
    return this.incidentMessage;
  }

  public IncidentStatus getIncidentStatus() {
    return this.incidentStatus;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public String getDefinitionKey() {
    return this.definitionKey;
  }

  public String getDefinitionVersion() {
    return this.definitionVersion;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public String getEngineAlias() {
    return this.engineAlias;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setCreateTime(OffsetDateTime createTime) {
    this.createTime = createTime;
  }

  public void setEndTime(OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public void setDurationInMs(Long durationInMs) {
    this.durationInMs = durationInMs;
  }

  public void setIncidentType(IncidentType incidentType) {
    this.incidentType = incidentType;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public void setFailedActivityId(String failedActivityId) {
    this.failedActivityId = failedActivityId;
  }

  public void setIncidentMessage(String incidentMessage) {
    this.incidentMessage = incidentMessage;
  }

  public void setIncidentStatus(IncidentStatus incidentStatus) {
    this.incidentStatus = incidentStatus;
  }

  @JsonIgnore
  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setDefinitionKey(String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public void setDefinitionVersion(String definitionVersion) {
    this.definitionVersion = definitionVersion;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @JsonIgnore
  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }

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
    final Object this$id = this.getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$createTime = this.getCreateTime();
    final Object other$createTime = other.getCreateTime();
    if (this$createTime == null
        ? other$createTime != null
        : !this$createTime.equals(other$createTime)) {
      return false;
    }
    final Object this$endTime = this.getEndTime();
    final Object other$endTime = other.getEndTime();
    if (this$endTime == null ? other$endTime != null : !this$endTime.equals(other$endTime)) {
      return false;
    }
    final Object this$durationInMs = this.getDurationInMs();
    final Object other$durationInMs = other.getDurationInMs();
    if (this$durationInMs == null
        ? other$durationInMs != null
        : !this$durationInMs.equals(other$durationInMs)) {
      return false;
    }
    final Object this$incidentType = this.getIncidentType();
    final Object other$incidentType = other.getIncidentType();
    if (this$incidentType == null
        ? other$incidentType != null
        : !this$incidentType.equals(other$incidentType)) {
      return false;
    }
    final Object this$activityId = this.getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$failedActivityId = this.getFailedActivityId();
    final Object other$failedActivityId = other.getFailedActivityId();
    if (this$failedActivityId == null
        ? other$failedActivityId != null
        : !this$failedActivityId.equals(other$failedActivityId)) {
      return false;
    }
    final Object this$incidentMessage = this.getIncidentMessage();
    final Object other$incidentMessage = other.getIncidentMessage();
    if (this$incidentMessage == null
        ? other$incidentMessage != null
        : !this$incidentMessage.equals(other$incidentMessage)) {
      return false;
    }
    final Object this$incidentStatus = this.getIncidentStatus();
    final Object other$incidentStatus = other.getIncidentStatus();
    if (this$incidentStatus == null
        ? other$incidentStatus != null
        : !this$incidentStatus.equals(other$incidentStatus)) {
      return false;
    }
    final Object this$processInstanceId = this.getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$definitionKey = this.getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionVersion = this.getDefinitionVersion();
    final Object other$definitionVersion = other.getDefinitionVersion();
    if (this$definitionVersion == null
        ? other$definitionVersion != null
        : !this$definitionVersion.equals(other$definitionVersion)) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$engineAlias = this.getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IncidentDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $createTime = this.getCreateTime();
    result = result * PRIME + ($createTime == null ? 43 : $createTime.hashCode());
    final Object $endTime = this.getEndTime();
    result = result * PRIME + ($endTime == null ? 43 : $endTime.hashCode());
    final Object $durationInMs = this.getDurationInMs();
    result = result * PRIME + ($durationInMs == null ? 43 : $durationInMs.hashCode());
    final Object $incidentType = this.getIncidentType();
    result = result * PRIME + ($incidentType == null ? 43 : $incidentType.hashCode());
    final Object $activityId = this.getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $failedActivityId = this.getFailedActivityId();
    result = result * PRIME + ($failedActivityId == null ? 43 : $failedActivityId.hashCode());
    final Object $incidentMessage = this.getIncidentMessage();
    result = result * PRIME + ($incidentMessage == null ? 43 : $incidentMessage.hashCode());
    final Object $incidentStatus = this.getIncidentStatus();
    result = result * PRIME + ($incidentStatus == null ? 43 : $incidentStatus.hashCode());
    final Object $processInstanceId = this.getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $definitionKey = this.getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionVersion = this.getDefinitionVersion();
    result = result * PRIME + ($definitionVersion == null ? 43 : $definitionVersion.hashCode());
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $engineAlias = this.getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    return result;
  }

  public String toString() {
    return "IncidentDto(id="
        + this.getId()
        + ", createTime="
        + this.getCreateTime()
        + ", endTime="
        + this.getEndTime()
        + ", durationInMs="
        + this.getDurationInMs()
        + ", incidentType="
        + this.getIncidentType()
        + ", activityId="
        + this.getActivityId()
        + ", failedActivityId="
        + this.getFailedActivityId()
        + ", incidentMessage="
        + this.getIncidentMessage()
        + ", incidentStatus="
        + this.getIncidentStatus()
        + ", processInstanceId="
        + this.getProcessInstanceId()
        + ", definitionKey="
        + this.getDefinitionKey()
        + ", definitionVersion="
        + this.getDefinitionVersion()
        + ", tenantId="
        + this.getTenantId()
        + ", engineAlias="
        + this.getEngineAlias()
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

    private String id;
    private OffsetDateTime createTime;
    private OffsetDateTime endTime;
    private Long durationInMs;
    private IncidentType incidentType;
    private String activityId;
    private String failedActivityId;
    private String incidentMessage;
    private IncidentStatus incidentStatus;
    private String processInstanceId;
    private String definitionKey;
    private String definitionVersion;
    private String tenantId;
    private String engineAlias;

    IncidentDtoBuilder() {}

    public IncidentDtoBuilder id(String id) {
      this.id = id;
      return this;
    }

    public IncidentDtoBuilder createTime(OffsetDateTime createTime) {
      this.createTime = createTime;
      return this;
    }

    public IncidentDtoBuilder endTime(OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public IncidentDtoBuilder durationInMs(Long durationInMs) {
      this.durationInMs = durationInMs;
      return this;
    }

    public IncidentDtoBuilder incidentType(IncidentType incidentType) {
      this.incidentType = incidentType;
      return this;
    }

    public IncidentDtoBuilder activityId(String activityId) {
      this.activityId = activityId;
      return this;
    }

    public IncidentDtoBuilder failedActivityId(String failedActivityId) {
      this.failedActivityId = failedActivityId;
      return this;
    }

    public IncidentDtoBuilder incidentMessage(String incidentMessage) {
      this.incidentMessage = incidentMessage;
      return this;
    }

    public IncidentDtoBuilder incidentStatus(IncidentStatus incidentStatus) {
      this.incidentStatus = incidentStatus;
      return this;
    }

    @JsonIgnore
    public IncidentDtoBuilder processInstanceId(String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public IncidentDtoBuilder definitionKey(String definitionKey) {
      this.definitionKey = definitionKey;
      return this;
    }

    public IncidentDtoBuilder definitionVersion(String definitionVersion) {
      this.definitionVersion = definitionVersion;
      return this;
    }

    public IncidentDtoBuilder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @JsonIgnore
    public IncidentDtoBuilder engineAlias(String engineAlias) {
      this.engineAlias = engineAlias;
      return this;
    }

    public IncidentDto build() {
      return new IncidentDto(
          this.id,
          this.createTime,
          this.endTime,
          this.durationInMs,
          this.incidentType,
          this.activityId,
          this.failedActivityId,
          this.incidentMessage,
          this.incidentStatus,
          this.processInstanceId,
          this.definitionKey,
          this.definitionVersion,
          this.tenantId,
          this.engineAlias);
    }

    public String toString() {
      return "IncidentDto.IncidentDtoBuilder(id="
          + this.id
          + ", createTime="
          + this.createTime
          + ", endTime="
          + this.endTime
          + ", durationInMs="
          + this.durationInMs
          + ", incidentType="
          + this.incidentType
          + ", activityId="
          + this.activityId
          + ", failedActivityId="
          + this.failedActivityId
          + ", incidentMessage="
          + this.incidentMessage
          + ", incidentStatus="
          + this.incidentStatus
          + ", processInstanceId="
          + this.processInstanceId
          + ", definitionKey="
          + this.definitionKey
          + ", definitionVersion="
          + this.definitionVersion
          + ", tenantId="
          + this.tenantId
          + ", engineAlias="
          + this.engineAlias
          + ")";
    }
  }
}
