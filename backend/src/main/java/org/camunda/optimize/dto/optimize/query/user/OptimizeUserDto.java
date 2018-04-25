package org.camunda.optimize.dto.optimize.query.user;

import java.time.OffsetDateTime;

public class OptimizeUserDto {

  private String id;
  private OffsetDateTime lastLoggedIn;
  private OffsetDateTime createdAt;
  private String createdBy;
  private String lastModifier;
  private OffsetDateTime lastModified;
  private PermissionsDto permissions;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public OffsetDateTime getLastLoggedIn() {
    return lastLoggedIn;
  }

  public void setLastLoggedIn(OffsetDateTime lastLoggedIn) {
    this.lastLoggedIn = lastLoggedIn;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public PermissionsDto getPermissions() {
    return permissions;
  }

  public void setPermissions(PermissionsDto permissions) {
    this.permissions = permissions;
  }
}
