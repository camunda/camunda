package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.RelativeDateFilterStartDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;


public class DateUtilHelper {

  public static void addStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate, BranchAnalysisQueryDto dto) {
    List<FilterDto> dateFilter = createFixedStartDateFilter(startDate, endDate);
    dto.getFilter().addAll(dateFilter);
  }

  public static List<FilterDto> createFixedStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    StartDateFilterDto filter = new StartDateFilterDto();
    FixedDateFilterDataDto filterData = new FixedDateFilterDataDto();

    filterData.setStart(startDate);
    filterData.setEnd(endDate);
    filter.setData(filterData);

    ArrayList<FilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<FilterDto> createFixedEndDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    EndDateFilterDto filterDto = new EndDateFilterDto();
    FixedDateFilterDataDto filterData = new FixedDateFilterDataDto();

    filterData.setStart(startDate);
    filterData.setEnd(endDate);
    filterDto.setData(filterData);

    ArrayList<FilterDto> filters = new ArrayList<>();
    filters.add(filterDto);

    return filters;
  }

  public static List<FilterDto> createRollingStartDateFilter(Long value, String unit) {
    StartDateFilterDto filter = new StartDateFilterDto();
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto();
    RelativeDateFilterStartDto startDate = new RelativeDateFilterStartDto();

    startDate.setUnit(unit);
    startDate.setValue(value);
    filterData.setStart(startDate);
    filter.setData(filterData);

    List<FilterDto> filters = new ArrayList<>();
    filters.add(filter);

    return filters;
  }

  public static List<FilterDto> createRollingEndDateFilter(Long value, String unit) {
    EndDateFilterDto filter = new EndDateFilterDto();
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto();
    RelativeDateFilterStartDto startDate = new RelativeDateFilterStartDto();

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
