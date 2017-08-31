package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExecutedFlowNodeFilterBuilder {

  private String operator = "=";
  private List<String> values = new ArrayList<>();
  private List<ExecutedFlowNodeFilterDto> executedFlowNodes = new ArrayList<>();

  public static ExecutedFlowNodeFilterBuilder construct() {
    return new ExecutedFlowNodeFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder equalOperator() {
    operator = "=";
    return this;
  }

  public ExecutedFlowNodeFilterBuilder unequalOperator() {
    operator = "!=";
    return this;
  }

  public ExecutedFlowNodeFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutedFlowNodeFilterBuilder and() {
    addNewFilter();
    return this;
  }

  private void addNewFilter() {
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setOperator(operator);
    executedFlowNodeFilterDto.setValues(new ArrayList<>(values));
    executedFlowNodes.add(executedFlowNodeFilterDto);
    values.clear();
    restoreDefaultOperator();
  }

  private void restoreDefaultOperator() {
    operator = "=";
  }

  public List<ExecutedFlowNodeFilterDto> build() {
    if (!values.isEmpty()) {
      addNewFilter();
    }
    List<ExecutedFlowNodeFilterDto> result = new ArrayList<>(executedFlowNodes);
    executedFlowNodes.clear();
    return result;
  }
}
