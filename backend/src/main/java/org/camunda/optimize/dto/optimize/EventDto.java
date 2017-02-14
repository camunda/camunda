package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class EventDto implements Serializable,OptimizeDto {
  private String activityId;
  private String state;
  private String activityInstanceId;
  private Date timestamp;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private Date startDate;
  private Date endDate;


  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }


  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }
}
