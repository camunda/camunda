package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;

public class ExecutedFlowNodeFilterBuilder {

  private String operator = IN;
  private List<String> values = new ArrayList<>();
  private List<ExecutedFlowNodeFilterDto> executedFlowNodes = new ArrayList<>();

  public static ExecutedFlowNodeFilterBuilder construct() {
    return new ExecutedFlowNodeFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder inOperator() {
    operator = IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder notInOperator() {
    operator = NOT_IN;
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
    operator = IN;
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
