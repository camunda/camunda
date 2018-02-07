package org.camunda.optimize.dto.optimize.query.sharing;

import java.io.Serializable;

/**
 * @author Askar Akhmerov
 */
public class SharingDto implements Serializable {
  private String type;
  private String id;
  private String resourceId;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }
}
