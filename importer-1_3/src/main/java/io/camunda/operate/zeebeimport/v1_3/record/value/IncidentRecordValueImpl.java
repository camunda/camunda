/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v1_3.record.value;

import io.camunda.operate.zeebeimport.v1_3.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class IncidentRecordValueImpl extends RecordValueWithPayloadImpl implements IncidentRecordValue {

  private ErrorType errorType;
  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long processDefinitionKey;
  private long processInstanceKey;
  private long elementInstanceKey;
  private long jobKey;
  private long variableScopeKey;

  public IncidentRecordValueImpl() {
  }

  @Override
  public ErrorType getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = errorType;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  public void setVariableScopeKey(long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    IncidentRecordValueImpl that = (IncidentRecordValueImpl) o;

    if (processDefinitionKey != that.processDefinitionKey)
      return false;
    if (processInstanceKey != that.processInstanceKey)
      return false;
    if (elementInstanceKey != that.elementInstanceKey)
      return false;
    if (jobKey != that.jobKey)
      return false;
    if (variableScopeKey != that.variableScopeKey)
      return false;
    if (errorType != null ? !errorType.equals(that.errorType) : that.errorType != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    return elementId != null ? elementId.equals(that.elementId) : that.elementId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + (int) (processDefinitionKey ^ (processDefinitionKey >>> 32));
    result = 31 * result + (int) (processInstanceKey ^ (processInstanceKey >>> 32));
    result = 31 * result + (int) (elementInstanceKey ^ (elementInstanceKey >>> 32));
    result = 31 * result + (int) (jobKey ^ (jobKey >>> 32));
    result = 31 * result + (int) (variableScopeKey ^ (variableScopeKey >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "IncidentRecordValueImpl{" + "errorType='" + errorType + '\'' + ", errorMessage='" + errorMessage + '\'' + ", bpmnProcessId='" + bpmnProcessId + '\''
        + ", elementId='" + elementId + '\'' + ", processDefinitionKey=" + processDefinitionKey + ", processInstanceKey=" + processInstanceKey + ", elementInstanceKey=" + elementInstanceKey + ", jobKey="
        + jobKey + ", variableScopeKey=" + variableScopeKey + "} " + super.toString();
  }
}
