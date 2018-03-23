package org.camunda.optimize.dto.optimize.query.sharing;

/**
 * @author Askar Akhmerov
 */
public class ShareStatusDto {
  private String id;
  private boolean shared;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isShared() {
    return shared;
  }

  public void setShared(boolean shared) {
    this.shared = shared;
  }
}
