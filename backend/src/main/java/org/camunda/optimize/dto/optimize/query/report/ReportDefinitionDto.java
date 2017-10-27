package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import java.time.LocalDateTime;

public class ReportDefinitionDto implements SortableFields {

  protected String id;
  protected String name;
  protected LocalDateTime lastModified;
  protected LocalDateTime created;
  protected String owner;
  protected String lastModifier;
  protected ReportDataDto data;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getLastModified() {
    return lastModified;
  }

  public LocalDateTime getCreated() {
    return created;
  }

  public void setCreated(LocalDateTime created) {
    this.created = created;
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

  public ReportDataDto getData() {
    return data;
  }

  public void setData(ReportDataDto data) {
    this.data = data;
  }
}
