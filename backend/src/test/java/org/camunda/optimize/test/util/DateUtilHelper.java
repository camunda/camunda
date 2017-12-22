package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.RollingDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.RollingDateFilterDataDto;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DateUtilHelper {

  public static void addDateFilter(String operator, String type, OffsetDateTime dateValue, HeatMapQueryDto dto) {
    List<FilterDto> dateFilter = createDateFilter(operator, type, dateValue);
    dto.getFilter().addAll(dateFilter);
  }

  public static List<FilterDto> createDateFilter(String operator, String type, OffsetDateTime dateValue) {
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    return Collections.singletonList(dateFilterDto);
  }

  public static List<FilterDto> createRollingDateFilter(Long value, String unit) {
    List<FilterDto> result = new ArrayList<>();
    RollingDateFilterDto filter = new RollingDateFilterDto();
    RollingDateFilterDataDto filterData = new RollingDateFilterDataDto();
    filterData.setUnit(unit);
    filterData.setValue(value);
    filter.setData(filterData);
    result.add(filter);
    return result;
  }

  public static List<FilterDto> createDurationFilter(String operator, int i, String unit) {
    List<FilterDto> result = new ArrayList<>();

    DurationFilterDto filter = new DurationFilterDto();

    DurationFilterDataDto filterData = new DurationFilterDataDto();
    filterData.setOperator(operator);
    filterData.setUnit(unit);
    filterData.setValue(Long.valueOf(i));

    filter.setData(filterData);
    result.add(filter);
    return result;
  }
}
