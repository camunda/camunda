package org.camunda.optimize.dto.optimize.query.report.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;

import org.camunda.optimize.dto.optimize.query.report.filter.data.FilterDataDto;

/**
 * TODO: write that there is actually also a type field
 * @param <DATA>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DateFilterDto.class, name = "date"),
    @JsonSubTypes.Type(value = VariableFilterDto.class, name = "variable"),
    @JsonSubTypes.Type(value = ExecutedFlowNodeFilterDto.class, name = "executedFlowNodes")
}
)
public abstract class FilterDto<DATA extends FilterDataDto> {

  protected DATA data;

  public DATA getData() {
    return data;
  }

  public void setData(DATA data) {
    this.data = data;
  }
}
