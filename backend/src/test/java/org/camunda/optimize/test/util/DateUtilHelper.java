package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.RollingDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.RollingDateFilterDataDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DateUtilHelper {

  public static void addDateFilter(String operator, String type, OffsetDateTime dateValue, ReportDataDto dto) {
    List<FilterDto> dateFilter = createDateFilter(operator, type, dateValue);
    dto.getFilter().addAll(dateFilter);
  }

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
    ArrayList<FilterDto> filterDtos = new ArrayList<>();
    filterDtos.add(dateFilterDto);
    return filterDtos;
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

  public static List<FilterDto> createDurationFilter(String operator, int filterValue, String unit) {
    List<FilterDto> result = new ArrayList<>();

    DurationFilterDto filter = new DurationFilterDto();

    DurationFilterDataDto filterData = new DurationFilterDataDto();
    filterData.setOperator(operator);
    filterData.setUnit(unit);
    filterData.setValue(Long.valueOf(filterValue));

    filter.setData(filterData);
    result.add(filter);
    return result;
  }
}
