package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.variable.VariableFilterDto;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class FilterMapDto {
  protected List<DateFilterDto> dates = new ArrayList<>();
  protected List<VariableFilterDto> variables = new ArrayList<>();

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
}
