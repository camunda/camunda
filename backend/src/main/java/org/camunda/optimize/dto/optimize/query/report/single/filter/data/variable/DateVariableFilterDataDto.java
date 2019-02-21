package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DateVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;

public class DateVariableFilterDataDto extends VariableFilterDataDto<DateVariableFilterSubDataDto> {
  
  protected DateVariableFilterDataDto() {
    this(null, null);
  }

  public DateVariableFilterDataDto(OffsetDateTime start, OffsetDateTime end) {
    this.type = VariableType.DATE;
    DateVariableFilterSubDataDto dataDto = new DateVariableFilterSubDataDto();
    dataDto.setStart(start);
    dataDto.setEnd(end);
    setData(dataDto);
  }

}
