/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.client.dto;

import lombok.Data;

import java.util.Date;

@Data
public class EngineIncidentDto {

  protected String id;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String executionId;
  protected Date incidentTimestamp;
  protected String incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String causeIncidentId;
  protected String rootCauseIncidentId;
  protected String configuration;
  protected String incidentMessage;
  protected String tenantId;
  protected String jobDefinitionId;
}
