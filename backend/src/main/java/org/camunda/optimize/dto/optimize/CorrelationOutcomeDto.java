package org.camunda.optimize.dto.optimize;

/**
 * @author Askar Akhmerov
 */
public class CorrelationOutcomeDto {
  private Long reached;
  private Long all;
  private String id;

  public Long getReached() {
    return reached;
  }

  public void setReached(Long reached) {
    this.reached = reached;
  }

  public Long getAll() {
    return all;
  }

  public void setAll(Long all) {
    this.all = all;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
