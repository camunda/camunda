/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.optimize.dto;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.util.Date;

public class ActivityListDto implements Serializable, OptimizeDto {

  protected String processDefinitionId;
  protected Date processInstanceStartDate;
  protected Date processInstanceEndDate;
  protected String[] activityList;

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Date getProcessInstanceStartDate() {
    return processInstanceStartDate;
  }

  public void setProcessInstanceStartDate(final Date processInstanceStartDate) {
    this.processInstanceStartDate = processInstanceStartDate;
  }

  public Date getProcessInstanceEndDate() {
    return processInstanceEndDate;
  }

  public void setProcessInstanceEndDate(final Date processInstanceEndDate) {
    this.processInstanceEndDate = processInstanceEndDate;
  }

  public String[] getActivityList() {
    return activityList;
  }

  public void setActivityList(final String[] activityList) {
    this.activityList = activityList;
  }
}
