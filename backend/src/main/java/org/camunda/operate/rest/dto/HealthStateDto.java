package org.camunda.operate.rest.dto;

/**
 * @author Svetlana Dorokhova.
 */
public class HealthStateDto {

  private String state;

  public HealthStateDto() {
  }

  public HealthStateDto(String state) {
    this.state = state;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    HealthStateDto that = (HealthStateDto) o;

    return state != null ? state.equals(that.state) : that.state == null;
  }

  @Override
  public int hashCode() {
    return state != null ? state.hashCode() : 0;
  }
}
