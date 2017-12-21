package org.camunda.optimize.dto.optimize.query.report.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.filter.data.FilterDataDto;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DateFilterDto.class, name = "date"),
    @JsonSubTypes.Type(value = RollingDateFilterDto.class, name = "rollingDate"),
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
