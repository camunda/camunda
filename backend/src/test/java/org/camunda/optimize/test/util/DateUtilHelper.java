package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


public class DateUtilHelper {

  public static void addStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate, BranchAnalysisQueryDto dto) {
    List<ProcessFilterDto> dateFilter = createFixedStartDateFilter(startDate, endDate);
    dto.getFilter().addAll(dateFilter);
  }

  public static List<ProcessFilterDto> createFixedStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    StartDateFilterDto filter = new StartDateFilterDto();
    FixedDateFilterDataDto filterData = new FixedDateFilterDataDto();

    filterData.setStart(startDate);
    filterData.setEnd(endDate);
    filter.setData(filterData);

    ArrayList<ProcessFilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<ProcessFilterDto> createFixedEndDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    EndDateFilterDto filterDto = new EndDateFilterDto();
    FixedDateFilterDataDto filterData = new FixedDateFilterDataDto();

    filterData.setStart(startDate);
    filterData.setEnd(endDate);
    filterDto.setData(filterData);

    ArrayList<ProcessFilterDto> filters = new ArrayList<>();
    filters.add(filterDto);

    return filters;
  }

  public static List<ProcessFilterDto> createRollingStartDateFilter(Long value, String unit) {
    StartDateFilterDto filter = new StartDateFilterDto();
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto();
    RelativeDateFilterStartDto startDate = new RelativeDateFilterStartDto();

    startDate.setUnit(unit);
    startDate.setValue(value);
    filterData.setStart(startDate);
    filter.setData(filterData);

    List<ProcessFilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<ProcessFilterDto> createRollingEndDateFilter(Long value, String unit) {
    EndDateFilterDto filter = new EndDateFilterDto();
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto();
    RelativeDateFilterStartDto startDate = new RelativeDateFilterStartDto();

    startDate.setUnit(unit);
    startDate.setValue(value);
    filterData.setStart(startDate);
    filter.setData(filterData);

    List<ProcessFilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<ProcessFilterDto> createDurationFilter(String operator, int filterValue, String unit) {
    List<ProcessFilterDto> result = new ArrayList<>();

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
