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
      final String id,
      final OffsetDateTime createTime,
      final OffsetDateTime endTime,
      final Long durationInMs,
      final IncidentType incidentType,
      final String activityId,
      final String failedActivityId,
      final String incidentMessage,
      final IncidentStatus incidentStatus,
      final String processInstanceId,
      final String definitionKey,
      final String definitionVersion,
      final String tenantId,
      final String engineAlias) {
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

  public void setId(final String id) {
    this.id = id;
  }

  public void setCreateTime(final OffsetDateTime createTime) {
    this.createTime = createTime;
  }

  public void setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public void setDurationInMs(final Long durationInMs) {
    this.durationInMs = durationInMs;
  }

  public void setIncidentType(final IncidentType incidentType) {
    this.incidentType = incidentType;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public void setFailedActivityId(final String failedActivityId) {
    this.failedActivityId = failedActivityId;
  }

  public void setIncidentMessage(final String incidentMessage) {
    this.incidentMessage = incidentMessage;
  }

  public void setIncidentStatus(final IncidentStatus incidentStatus) {
    this.incidentStatus = incidentStatus;
  }

  @JsonIgnore
  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public void setDefinitionVersion(final String definitionVersion) {
    this.definitionVersion = definitionVersion;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @JsonIgnore
  public void setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IncidentDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
