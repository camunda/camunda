/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

public class ProcessInstanceCoreStatisticsDto {

  private Long running = 0L;
  private Long active = 0L;
  private Long withIncidents = 0L;

  public Long getRunning() {
    return running;
  }

  public ProcessInstanceCoreStatisticsDto setRunning(Long running) {
    this.running = running;
    return this;
  }

  public Long getActive() {
    return active;
  }

  public ProcessInstanceCoreStatisticsDto setActive(Long active) {
    this.active = active;
    return this;
  }

  public Long getWithIncidents() {
    return withIncidents;
  }

  public ProcessInstanceCoreStatisticsDto setWithIncidents(Long withIncidents) {
    this.withIncidents = withIncidents;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ProcessInstanceCoreStatisticsDto that = (ProcessInstanceCoreStatisticsDto) o;

    if (running != null ? !running.equals(that.running) : that.running != null)
      return false;
    if (active != null ? !active.equals(that.active) : that.active != null)
      return false;

    return withIncidents != null ? withIncidents.equals(that.withIncidents) : that.withIncidents == null;
  }

  @Override
  public int hashCode() {
    int result = running != null ? running.hashCode() : 0;
    result = 31 * result + (active != null ? active.hashCode() : 0);
    result = 31 * result + (withIncidents != null ? withIncidents.hashCode() : 0);
    return result;
  }

}
