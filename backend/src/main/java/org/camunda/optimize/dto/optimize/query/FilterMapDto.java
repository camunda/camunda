package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableFilterDto;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class FilterMapDto {
  protected List<DateFilterDto> dates = new ArrayList<>();
  protected List<VariableFilterDto> variables = new ArrayList<>();
  protected List<ExecutedFlowNodeFilterDto> executedFlowNodes = new ArrayList<>();

  public List<DateFilterDto> getDates() {
    return dates;
  }

  public void setDates(List<DateFilterDto> dates) {
    this.dates = dates;
  }

  public List<VariableFilterDto> getVariables() {
    return variables;
  }

  public void setVariables(List<VariableFilterDto> variables) {
    this.variables = variables;
  }

  public List<ExecutedFlowNodeFilterDto> getExecutedFlowNodes() {
    return executedFlowNodes;
  }

  public void setExecutedFlowNodes(List<ExecutedFlowNodeFilterDto> executedFlowNodes) {
    this.executedFlowNodes = executedFlowNodes;
  }
}
