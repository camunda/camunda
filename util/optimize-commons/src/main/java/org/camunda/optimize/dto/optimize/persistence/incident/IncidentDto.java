/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.persistence.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
@Builder
public class IncidentDto implements Serializable, OptimizeDto {

  @JsonIgnore
  private String processInstanceId;
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;
  @JsonIgnore
  private String engineAlias;

  protected String id;
  protected OffsetDateTime createTime;
  protected OffsetDateTime endTime;
  protected Long durationInMs;
  protected IncidentType incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String incidentMessage;
  protected IncidentStatus incidentStatus;
}
