package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DataUtilHelper {

  public static void addDateFilter(String operator, String type, Date dateValue, HeatMapQueryDto dto) {
    List<FilterDto> dateFilter = createDateFilter(operator, type, dateValue);
    dto.getFilter().addAll(dateFilter);
  }

  public static List<FilterDto> createDateFilter(String operator, String type, Date dateValue) {
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    return Collections.singletonList(dateFilterDto);
  }

}
