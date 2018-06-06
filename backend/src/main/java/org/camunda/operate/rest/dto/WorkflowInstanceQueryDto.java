package org.camunda.operate.rest.dto;

/**
 * @author Svetlana Dorokhova.
 */
public class WorkflowInstanceQueryDto {

  private boolean running;
  private boolean completed;
  private boolean withIncidents;
  private boolean withoutIncidents;

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

  public boolean isWithIncidents() {
    return withIncidents;
  }

  public void setWithIncidents(boolean withIncidents) {
    this.withIncidents = withIncidents;
  }

  public boolean isWithoutIncidents() {
    return withoutIncidents;
  }

  public void setWithoutIncidents(boolean withoutIncidents) {
    this.withoutIncidents = withoutIncidents;
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
    if (completed != that.completed)
      return false;
    if (withIncidents != that.withIncidents)
      return false;
    return withoutIncidents == that.withoutIncidents;
  }

  @Override
  public int hashCode() {
    int result = (running ? 1 : 0);
    result = 31 * result + (completed ? 1 : 0);
    result = 31 * result + (withIncidents ? 1 : 0);
    result = 31 * result + (withoutIncidents ? 1 : 0);
    return result;
  }
}
