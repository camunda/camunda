package org.camunda.operate.rest.dto;

public class WorkflowInstanceQueryDto {

  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean cancelled;

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

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
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
    return cancelled == that.cancelled;
  }

  @Override
  public int hashCode() {
    int result = (running ? 1 : 0);
    result = 31 * result + (active ? 1 : 0);
    result = 31 * result + (incidents ? 1 : 0);
    result = 31 * result + (finished ? 1 : 0);
    result = 31 * result + (completed ? 1 : 0);
    result = 31 * result + (cancelled ? 1 : 0);
    return result;
  }
}
