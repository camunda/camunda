package org.camunda.optimize.dto.optimize.query.report.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.filter.data.variable.data.DateVariableFilterSubDataDto;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.util.VariableHelper.DATE_TYPE;

public class DateVariableFilterDataDto extends VariableFilterDataDto<DateVariableFilterSubDataDto> {

  public DateVariableFilterDataDto() {
    this.type = DATE_TYPE;
  }

  public DateVariableFilterDataDto(OffsetDateTime start, OffsetDateTime end) {
    this.type = DATE_TYPE;
    DateVariableFilterSubDataDto dataDto = new DateVariableFilterSubDataDto();
    dataDto.setStart(start);
    dataDto.setEnd(end);
    setData(dataDto);
  }

}
