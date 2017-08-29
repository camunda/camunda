package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExecutedFlowNodeFilterBuilder {

  private List<FlowNodeIdList> andLinkedFlowNodeIds = new ArrayList<>();
  private List<String> orLinkedFlowNodeIds = new ArrayList<>();

  public static ExecutedFlowNodeFilterBuilder construct() {
    return new ExecutedFlowNodeFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder id(String flowNodeId) {
    orLinkedFlowNodeIds.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder linkIdsByOr(String... flowNodeIds) {
    orLinkedFlowNodeIds.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutedFlowNodeFilterBuilder and() {
    FlowNodeIdList flowNodeIdList = new FlowNodeIdList();
    flowNodeIdList.getOrLinkedIds().addAll(orLinkedFlowNodeIds);
    andLinkedFlowNodeIds.add(flowNodeIdList);
    orLinkedFlowNodeIds.clear();
    return this;
  }

  public ExecutedFlowNodeFilterDto build() {
    if (!orLinkedFlowNodeIds.isEmpty()) {
      FlowNodeIdList flowNodeIdList = new FlowNodeIdList();
      flowNodeIdList.getOrLinkedIds().addAll(orLinkedFlowNodeIds);
      andLinkedFlowNodeIds.add(flowNodeIdList);
      orLinkedFlowNodeIds.clear();
    }
    ExecutedFlowNodeFilterDto flowNodeFilter = new ExecutedFlowNodeFilterDto();
    flowNodeFilter.getAndLinkedIds().addAll(andLinkedFlowNodeIds);
    andLinkedFlowNodeIds.clear();
    return flowNodeFilter;
  }
}
