package org.camunda.optimize.dto.optimize.query.report;

import java.time.LocalDateTime;

public class ReportDefinitionUpdateDto {

  protected String name;
  protected LocalDateTime lastModified;
  protected String owner;
  protected String lastModifier;
  protected ReportDataDto data;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    if (name != null) {
      this.name = name;
    }
  }

  public LocalDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(LocalDateTime lastModified) {
    if (lastModified != null) {
      this.lastModified = lastModified;
    }
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    if (owner != null) {
      this.owner = owner;
    }
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(String lastModifier) {
    if (lastModifier != null) {
      this.lastModifier = lastModifier;
    }
  }

  public ReportDataDto getData() {
    return data;
  }

  public void setData(ReportDataDto data) {
    if (data != null) {
      this.data = data;
    }
  }
}
