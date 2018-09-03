package org.camunda.optimize.upgrade.wrapper;


public class SourceWrapper {
  private String index;
  private String type;

  public SourceWrapper(String sourceIndex, String type) {
    this.index = sourceIndex;
    this.type = type;
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
