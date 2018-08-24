package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.FixedStartDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.RelativeStartDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.RelativeStartDateFilterStartDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


public class DateUtilHelper {

  public static void addStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate, BranchAnalysisQueryDto dto) {
    List<FilterDto> dateFilter = createFixedStartDateFilter(startDate, endDate);
    dto.getFilter().addAll(dateFilter);
  }

  public static List<FilterDto> createFixedStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    StartDateFilterDto filter = new StartDateFilterDto();
    FixedStartDateFilterDataDto filterData = new FixedStartDateFilterDataDto();

    filterData.setStart(startDate);
    filterData.setEnd(endDate);
    filter.setData(filterData);

    ArrayList<FilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<FilterDto> createRollingDateFilter(Long value, String unit) {
    StartDateFilterDto filter = new StartDateFilterDto();
    RelativeStartDateFilterDataDto filterData = new RelativeStartDateFilterDataDto();
    RelativeStartDateFilterStartDto startDate = new RelativeStartDateFilterStartDto();

    startDate.setUnit(unit);
    startDate.setValue(value);
    filterData.setStart(startDate);
    filter.setData(filterData);

    List<FilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
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
