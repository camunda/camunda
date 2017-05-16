package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DataUtilHelper {

  public static void addDateFilter(String operator, String type, Date dateValue, HeatMapQueryDto dto) {
    FilterMapDto filter ;
    if (dto.getFilter() == null) {
      filter = new FilterMapDto();
      filter.setDates(new ArrayList<>());
      dto.setFilter(filter);
    } else {
      filter = dto.getFilter();
    }
    List<DateFilterDto> dates = instantiateDateFilterDto(filter.getDates(), operator, type, dateValue, filter);
    filter.setDates(dates);
  }

  private static List<DateFilterDto> instantiateDateFilterDto(List<DateFilterDto> dates, String operator, String type, Date dateValue, FilterMapDto filter) {
    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);
    dates.add(date);
    filter.setDates(dates);
    return dates;
  }
}
