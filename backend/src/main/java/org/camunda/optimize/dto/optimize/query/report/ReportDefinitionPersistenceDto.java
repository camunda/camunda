package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportDefinitionPersistenceDto {

  protected String id;
  protected String name;
  protected LocalDateTime lastModified;
  protected LocalDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String data;

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

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }
}
