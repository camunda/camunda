package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.List;

public class FlowNodeIdList {

  protected List<String> orLinkedIds = new ArrayList<>();

  public List<String> getOrLinkedIds() {
    return orLinkedIds;
  }

  public void setOrLinkedIds(List<String> orLinkedIds) {
    this.orLinkedIds = orLinkedIds;
  }
}
