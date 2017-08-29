package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.List;

public class ExecutedFlowNodeFilterDto {

  protected List<FlowNodeIdList> andLinkedIds = new ArrayList<>();

  public List<FlowNodeIdList> getAndLinkedIds() {
    return andLinkedIds;
  }

  public void setAndLinkedIds(List<FlowNodeIdList> andLinkedIds) {
    this.andLinkedIds = andLinkedIds;
  }
}
