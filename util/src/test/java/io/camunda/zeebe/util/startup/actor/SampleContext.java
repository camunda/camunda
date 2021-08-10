package io.camunda.zeebe.util.startup.actor;

import java.util.Objects;

final class SampleContext {
  private String propertyA = "defaultA";
  private String propertyB = "defaultB";

  String getPropertyA() {
    return propertyA;
  }

  void setPropertyA(final String propertyA) {
    this.propertyA = propertyA;
  }

  String getPropertyB() {
    return propertyB;
  }

  void setPropertyB(final String propertyB) {
    this.propertyB = propertyB;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPropertyA(), getPropertyB());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SampleContext)) {
      return false;
    }
    final SampleContext that = (SampleContext) o;
    return Objects.equals(getPropertyA(), that.getPropertyA())
        && Objects.equals(getPropertyB(), that.getPropertyB());
  }

  @Override
  public String toString() {
    return "SampleContext{"
        + "propertyA='"
        + propertyA
        + '\''
        + ", propertyB='"
        + propertyB
        + '\''
        + '}';
  }
}
