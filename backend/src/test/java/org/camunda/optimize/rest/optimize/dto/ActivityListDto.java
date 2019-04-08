/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.optimize.dto;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.util.Date;

public class ActivityListDto implements Serializable,OptimizeDto {

  protected String processDefinitionId;
  protected Date processInstanceStartDate;
  protected Date processInstanceEndDate;
  protected String[] activityList;

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Date getProcessInstanceStartDate() {
    return processInstanceStartDate;
  }

  public void setProcessInstanceStartDate(Date processInstanceStartDate) {
    this.processInstanceStartDate = processInstanceStartDate;
  }

  public Date getProcessInstanceEndDate() {
    return processInstanceEndDate;
  }

  public void setProcessInstanceEndDate(Date processInstanceEndDate) {
    this.processInstanceEndDate = processInstanceEndDate;
  }

  public String[] getActivityList() {
    return activityList;
  }

  public void setActivityList(String[] activityList) {
    this.activityList = activityList;
  }

}
