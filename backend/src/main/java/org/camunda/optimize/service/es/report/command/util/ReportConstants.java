package org.camunda.optimize.service.es.report.command.util;

public class ReportConstants {

  public static final String VIEW_RAW_DATA_OPERATION = "rawData";
  public static final String VIEW_COUNT_OPERATION = "count";
  public static final String VIEW_AVERAGE_OPERATION = "avg";
  public static final String VIEW_MIN_OPERATION = "min";
  public static final String VIEW_MAX_OPERATION = "max";
  public static final String VIEW_MEDIAN_OPERATION = "median";

  public static final String VIEW_FLOW_NODE_ENTITY = "flowNode";
  public static final String VIEW_PROCESS_INSTANCE_ENTITY = "processInstance";

  public static final String VIEW_FREQUENCY_PROPERTY = "frequency";
  public static final String VIEW_DURATION_PROPERTY = "duration";

  public static final String GROUP_BY_FLOW_NODES_TYPE = "flowNodes";
  public static final String GROUP_BY_NONE_TYPE = "none";
  public static final String GROUP_BY_START_DATE_TYPE = "startDate";
  public static final String GROUP_BY_VARIABLE_TYPE = "variable";

  public static final String DATE_UNIT_YEAR = "year";
  public static final String DATE_UNIT_MONTH = "month";
  public static final String DATE_UNIT_WEEK = "week";
  public static final String DATE_UNIT_DAY = "day";
  public static final String DATE_UNIT_HOUR = "hour";

  public static final String TABLE_VISUALIZATION = "table";
  public static final String HEAT_VISUALIZATION = "heat";
  public static final String SINGLE_NUMBER_VISUALIZATION = "number";

  public static final String ALL_VERSIONS = "ALL";

  public static final String FIXED_DATE_FILTER = "fixed";
  public static final String RELATIVE_DATE_FILTER = "relative";

  public static final String SINGLE_REPORT_TYPE = "single";
  public static final String COMBINED_REPORT_TYPE = "combined";

  public static final String RAW_RESULT_TYPE = "raw";
  public static final String MAP_RESULT_TYPE = "map";
  public static final String NUMBER_RESULT_TYPE = "number";
}
