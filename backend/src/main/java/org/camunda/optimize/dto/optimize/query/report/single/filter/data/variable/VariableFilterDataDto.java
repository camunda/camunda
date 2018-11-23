package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import static org.camunda.optimize.service.util.VariableHelper.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StringVariableFilterDataDto.class, name = STRING_TYPE),
    @JsonSubTypes.Type(value = ShortVariableFilterDataDto.class, name = SHORT_TYPE),
    @JsonSubTypes.Type(value = LongVariableFilterDataDto.class, name = LONG_TYPE),
    @JsonSubTypes.Type(value = DoubleVariableFilterDataDto.class, name = DOUBLE_TYPE),
    @JsonSubTypes.Type(value = IntegerVariableFilterDataDto.class, name = INTEGER_TYPE),
    @JsonSubTypes.Type(value = BooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE),
    @JsonSubTypes.Type(value = DateVariableFilterDataDto.class, name = DATE_TYPE),
})

public abstract class VariableFilterDataDto<DATA> implements FilterDataDto {
  @JsonProperty
  protected String type;

  protected String name;
  protected DATA data;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public DATA getData() {
    return data;
  }

  public void setData(DATA data) {
    this.data = data;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
