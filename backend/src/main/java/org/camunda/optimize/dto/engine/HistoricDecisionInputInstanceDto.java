/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricDecisionInputInstanceDto implements EngineDto {
  private String id;
  private String type;
  private Object value;
  private String decisionInstanceId;
  private String clauseId;
  private String clauseName;
  private String errorMessage;
  private Date createTime;
  private Date removalTime;
  private String rootProcessInstanceId;

  public HistoricDecisionInputInstanceDto() {
  }

  public String getId() {
    return this.id;
  }

  public String getType() {
    return type;
  }

  public Object getValue() {
    return value;
  }

  public String getDecisionInstanceId() {
    return this.decisionInstanceId;
  }

  public String getClauseId() {
    return this.clauseId;
  }

  public String getClauseName() {
    return this.clauseName;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  public Date getCreateTime() {
    return this.createTime;
  }

  public Date getRemovalTime() {
    return this.removalTime;
  }

  public String getRootProcessInstanceId() {
    return this.rootProcessInstanceId;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  public void setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public void setClauseId(final String clauseId) {
    this.clauseId = clauseId;
  }

  public void setClauseName(final String clauseName) {
    this.clauseName = clauseName;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setCreateTime(final Date createTime) {
    this.createTime = createTime;
  }

  public void setRemovalTime(final Date removalTime) {
    this.removalTime = removalTime;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }
}
