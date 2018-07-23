package org.camunda.optimize.dto.optimize.query.report.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.group.value.GroupByValueDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_VARIABLE_TYPE;


/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * group by type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartDateGroupByDto.class, name = GROUP_BY_START_DATE_TYPE),
    @JsonSubTypes.Type(value = FlowNodesGroupByDto.class, name = GROUP_BY_FLOW_NODES_TYPE),
    @JsonSubTypes.Type(value = NoneGroupByDto.class, name = GROUP_BY_NONE_TYPE),
    @JsonSubTypes.Type(value = VariableGroupByDto.class, name = GROUP_BY_VARIABLE_TYPE)
}
)
public abstract class GroupByDto<VALUE extends GroupByValueDto>{

  @JsonProperty
  protected String type;
  protected VALUE value;

  public VALUE getValue() {
    return value;
  }

  public void setValue(VALUE value) {
    this.value = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @JsonIgnore
  public String getKey() {
    return type;
  }

  @Override
  public String toString() {
    return type;
  }
}
