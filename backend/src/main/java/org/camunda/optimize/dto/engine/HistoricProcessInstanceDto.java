package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricProcessInstanceDto implements EngineDto {

  protected String id;
  protected String businessKey;
  protected String processDefinitionId;
  protected String processDefinitionKey;
  protected String processDefinitionName;
  protected Date startTime;
  protected Date endTime;
  protected Long durationInMillis;
  protected String startUserId;
  protected String startActivityId;
  protected String deleteReason;
  protected String superProcessInstanceId;
  protected String superCaseInstanceId;
  protected String caseInstanceId;
  protected String tenantId;
  protected String state;

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public void setProcessDefinitionName(String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public Date getStartTime() {
    return startTime;
  }

  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  public Long getDurationInMillis() {
    return durationInMillis;
  }

  public void setDurationInMillis(Long durationInMillis) {
    this.durationInMillis = durationInMillis;
  }

  public String getStartUserId() {
    return startUserId;
  }

  public void setStartUserId(String startUserId) {
    this.startUserId = startUserId;
  }

  public String getStartActivityId() {
    return startActivityId;
  }

  public void setStartActivityId(String startActivityId) {
    this.startActivityId = startActivityId;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public void setDeleteReason(String deleteReason) {
    this.deleteReason = deleteReason;
  }

  public String getSuperProcessInstanceId() {
    return superProcessInstanceId;
  }

  public void setSuperProcessInstanceId(String superProcessInstanceId) {
    this.superProcessInstanceId = superProcessInstanceId;
  }

  public String getSuperCaseInstanceId() {
    return superCaseInstanceId;
  }

  public void setSuperCaseInstanceId(String superCaseInstanceId) {
    this.superCaseInstanceId = superCaseInstanceId;
  }

  public String getCaseInstanceId() {
    return caseInstanceId;
  }

  public void setCaseInstanceId(String caseInstanceId) {
    this.caseInstanceId = caseInstanceId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }
}
