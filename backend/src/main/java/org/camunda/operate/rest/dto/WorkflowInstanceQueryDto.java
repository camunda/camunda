package org.camunda.operate.rest.dto;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.rest.exception.InvalidRequestException;

public class WorkflowInstanceQueryDto {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";

  public static final List<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new ArrayList<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
  }

  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean canceled;

  private List<String> workflowInstanceIds;

  private String errorMessage;

  private SortingDto sorting;

  public WorkflowInstanceQueryDto() {
  }

  public boolean isRunning() {
    return running;
  }

  public void setRunning(boolean running) {
    this.running = running;
  }

  public boolean isCompleted() {
    return completed;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public void setIncidents(boolean incidents) {
    this.incidents = incidents;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }

  public List<String> getWorkflowInstanceIds() {
    return workflowInstanceIds;
  }

  public void setWorkflowInstanceIds(List<String> workflowInstanceIds) {
    this.workflowInstanceIds = workflowInstanceIds;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public SortingDto getSorting() {
    return sorting;
  }

  public void setSorting(SortingDto sorting) {
    if (sorting != null && !VALID_SORT_BY_VALUES.contains(sorting.getSortBy())) {
      throw new InvalidRequestException("SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowInstanceQueryDto that = (WorkflowInstanceQueryDto) o;

    if (running != that.running)
      return false;
    if (active != that.active)
      return false;
    if (incidents != that.incidents)
      return false;
    if (finished != that.finished)
      return false;
    if (completed != that.completed)
      return false;
    if (canceled != that.canceled)
      return false;
    if (workflowInstanceIds != null ? !workflowInstanceIds.equals(that.workflowInstanceIds) : that.workflowInstanceIds != null)
      return false;
    return sorting != null ? sorting.equals(that.sorting) : that.sorting == null;
  }

  @Override
  public int hashCode() {
    int result = (running ? 1 : 0);
    result = 31 * result + (active ? 1 : 0);
    result = 31 * result + (incidents ? 1 : 0);
    result = 31 * result + (finished ? 1 : 0);
    result = 31 * result + (completed ? 1 : 0);
    result = 31 * result + (canceled ? 1 : 0);
    result = 31 * result + (workflowInstanceIds != null ? workflowInstanceIds.hashCode() : 0);
    result = 31 * result + (sorting != null ? sorting.hashCode() : 0);
    return result;
  }
}
