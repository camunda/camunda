package org.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardDefinitionUpdateDto {

  protected String name;
  protected LocalDateTime lastModified;
  protected String owner;
  protected String lastModifier;
  protected List<ReportLocationDto> reports;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(LocalDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public List<ReportLocationDto> getReports() {
    return reports;
  }

  public void setReports(List<ReportLocationDto> reports) {
    this.reports = reports;
  }
}
