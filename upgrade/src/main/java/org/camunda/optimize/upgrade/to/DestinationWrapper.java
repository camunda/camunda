package org.camunda.optimize.upgrade.to;

/**
 * @author Askar Akhmerov
 */
public class DestinationWrapper {
  private String index;

  public DestinationWrapper(String destinationIndex) {
    this.index = destinationIndex;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }
}
