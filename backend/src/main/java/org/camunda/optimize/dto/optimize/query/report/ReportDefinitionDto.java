package org.camunda.optimize.dto.optimize.query.report;

import java.util.Date;

public class ReportDefinitionDto {

  protected String id;
  protected String name;
  protected Date lastModified;
  protected Date created;
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

  public Date getLastModified() {
    return lastModified;
  }

  public Date getCreated() {
    return created;
  }

  public void setCreated(Date created) {
    this.created = created;
  }

  public void setLastModified(Date lastModified) {
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
