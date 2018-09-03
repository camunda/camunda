package org.camunda.optimize.upgrade.wrapper;


public class DestinationWrapper {
  private String index;
  private String type;

  public DestinationWrapper(String destinationIndex, String destinationTyp) {
    this.index = destinationIndex;
    this.type = destinationTyp;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
