package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.DateDto;
import org.camunda.optimize.dto.optimize.FilterDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DataUtilHelper {

  public static void addDateFilter(String operator, String type, Date dateValue, HeatMapQueryDto dto) {
    FilterDto filter = new FilterDto();
    List<DateDto> dates = new ArrayList<>();
    DateDto date = new DateDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);
    dates.add(date);
    filter.setDates(dates);
    dto.setFilter(filter);
  }
}
