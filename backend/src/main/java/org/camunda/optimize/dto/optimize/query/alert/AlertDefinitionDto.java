package org.camunda.optimize.dto.optimize.query.alert;

import java.time.OffsetDateTime;

/**
 * @author Askar Akhmerov
 */
public class AlertDefinitionDto extends AlertCreationDto {
  public static final String GRATER = ">";
  public static final String LESS = "<";

  protected String id;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected boolean triggered;
  protected boolean fixNotification;

  @Override
  public boolean isFixNotification() {
    return fixNotification;
  }

  @Override
  public void setFixNotification(boolean fixNotification) {
    this.fixNotification = fixNotification;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(OffsetDateTime created) {
    this.created = created;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public boolean isTriggered() {
    return triggered;
  }

  public void setTriggered(boolean triggered) {
    this.triggered = triggered;
  }
}
