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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class IncidentDto implements Serializable, OptimizeDto {

  @JsonIgnore private String processInstanceId;
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;
  @JsonIgnore private String engineAlias;

  protected String id;
  protected OffsetDateTime createTime;
  protected OffsetDateTime endTime;
  protected Long durationInMs;
  protected IncidentType incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String incidentMessage;
  protected IncidentStatus incidentStatus;

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
}
