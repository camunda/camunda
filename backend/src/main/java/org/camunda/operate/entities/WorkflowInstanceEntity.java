package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.variables.BooleanVariableEntity;
import org.camunda.operate.entities.variables.DoubleVariableEntity;
import org.camunda.operate.entities.variables.LongVariableEntity;
import org.camunda.operate.entities.variables.StringVariableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class WorkflowInstanceEntity extends OperateZeebeEntity {

  private String workflowId;
  private String workflowName;
  private Integer workflowVersion;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceState state;

  //TODO remove me
  private String businessKey;

  private long position;

  private String topicName;

  private List<IncidentEntity> incidents = new ArrayList<>();

  private List<ActivityInstanceEntity> activities = new ArrayList<>();

  private List<OperationEntity> operations = new ArrayList<>();

  private List<StringVariableEntity> stringVariables = new ArrayList<>();

  private List<LongVariableEntity> longVariables = new ArrayList<>();

  private List<DoubleVariableEntity> doubleVariables = new ArrayList<>();

  private List<BooleanVariableEntity> booleanVariables = new ArrayList<>();

  private List<SequenceFlowEntity> sequenceFlows = new ArrayList<>();

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  public Integer getWorkflowVersion() {
    return workflowVersion;
  }

  public void setWorkflowVersion(Integer workflowVersion) {
    this.workflowVersion = workflowVersion;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public WorkflowInstanceState getState() {
    return state;
  }

  public void setState(WorkflowInstanceState state) {
    this.state = state;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public List<IncidentEntity> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentEntity> incidents) {
    this.incidents = incidents;
  }

  public List<ActivityInstanceEntity> getActivities() {
    return activities;
  }

  public void setActivities(List<ActivityInstanceEntity> activityInstances) {
    this.activities = activityInstances;
  }

  public List<OperationEntity> getOperations() {
    return operations;
  }

  public void setOperations(List<OperationEntity> operations) {
    this.operations = operations;
  }

  public List<StringVariableEntity> getStringVariables() {
    return stringVariables;
  }

  public void setStringVariables(List<StringVariableEntity> stringVariables) {
    this.stringVariables = stringVariables;
  }

  public List<LongVariableEntity> getLongVariables() {
    return longVariables;
  }

  public void setLongVariables(List<LongVariableEntity> longVariables) {
    this.longVariables = longVariables;
  }

  public List<DoubleVariableEntity> getDoubleVariables() {
    return doubleVariables;
  }

  public void setDoubleVariables(List<DoubleVariableEntity> doubleVariables) {
    this.doubleVariables = doubleVariables;
  }

  public List<BooleanVariableEntity> getBooleanVariables() {
    return booleanVariables;
  }

  public void setBooleanVariables(List<BooleanVariableEntity> booleanVariables) {
    this.booleanVariables = booleanVariables;
  }

  public List<SequenceFlowEntity> getSequenceFlows() {
    return sequenceFlows;
  }

  public void setSequenceFlows(List<SequenceFlowEntity> sequenceFlows) {
    this.sequenceFlows = sequenceFlows;
  }

  @JsonIgnore
  public int countVariables() {
    return this.getStringVariables().size() + this.getLongVariables().size() + this.getDoubleVariables().size() + this.getBooleanVariables().size();
  }

  public long getPosition() {
    return position;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowInstanceEntity that = (WorkflowInstanceEntity) o;

    if (position != that.position)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (workflowName != null ? !workflowName.equals(that.workflowName) : that.workflowName != null)
      return false;
    if (workflowVersion != null ? !workflowVersion.equals(that.workflowVersion) : that.workflowVersion != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (state != that.state)
      return false;
    if (businessKey != null ? !businessKey.equals(that.businessKey) : that.businessKey != null)
      return false;
    if (topicName != null ? !topicName.equals(that.topicName) : that.topicName != null)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    if (activities != null ? !activities.equals(that.activities) : that.activities != null)
      return false;
    if (operations != null ? !operations.equals(that.operations) : that.operations != null)
      return false;
    if (stringVariables != null ? !stringVariables.equals(that.stringVariables) : that.stringVariables != null)
      return false;
    if (longVariables != null ? !longVariables.equals(that.longVariables) : that.longVariables != null)
      return false;
    if (doubleVariables != null ? !doubleVariables.equals(that.doubleVariables) : that.doubleVariables != null)
      return false;
    if (booleanVariables != null ? !booleanVariables.equals(that.booleanVariables) : that.booleanVariables != null)
      return false;
    return sequenceFlows != null ? sequenceFlows.equals(that.sequenceFlows) : that.sequenceFlows == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (workflowVersion != null ? workflowVersion.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (businessKey != null ? businessKey.hashCode() : 0);
    result = 31 * result + (int) (position ^ (position >>> 32));
    result = 31 * result + (topicName != null ? topicName.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (activities != null ? activities.hashCode() : 0);
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    result = 31 * result + (stringVariables != null ? stringVariables.hashCode() : 0);
    result = 31 * result + (longVariables != null ? longVariables.hashCode() : 0);
    result = 31 * result + (doubleVariables != null ? doubleVariables.hashCode() : 0);
    result = 31 * result + (booleanVariables != null ? booleanVariables.hashCode() : 0);
    result = 31 * result + (sequenceFlows != null ? sequenceFlows.hashCode() : 0);
    return result;
  }
}
