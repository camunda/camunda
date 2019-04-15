/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

public class ReportConstants {

  // report data structure constants
  public static final String VIEW_RAW_DATA_OPERATION = "rawData";
  public static final String VIEW_COUNT_OPERATION = "count";

  public static final String VIEW_FLOW_NODE_ENTITY = "flowNode";
  public static final String VIEW_USER_TASK_ENTITY = "userTask";
  public static final String VIEW_PROCESS_INSTANCE_ENTITY = "processInstance";

  public static final String VIEW_DECISION_MATCHED_RULE_ENTITY = "matchedRule";
  public static final String VIEW_DECISION_INSTANCE_ENTITY = "decisionInstance";

  public static final String VIEW_FREQUENCY_PROPERTY = "frequency";
  public static final String VIEW_DURATION_PROPERTY = "duration";
  public static final String VIEW_IDLE_DURATION_PROPERTY = "idleDuration";
  public static final String VIEW_WORK_DURATION_PROPERTY = "workDuration";
  public static final String VIEW_RAW_DATA_PROPERTY = "rawData";

  public static final String GROUP_BY_FLOW_NODES_TYPE = "flowNodes";
  public static final String GROUP_BY_USER_TASKS_TYPE = "userTasks";
  public static final String GROUP_BY_NONE_TYPE = "none";
  public static final String GROUP_BY_START_DATE_TYPE = "startDate";
  public static final String GROUP_BY_VARIABLE_TYPE = "variable";

  public static final String GROUP_BY_EVALUATION_DATE_TYPE = "evaluationDateTime";
  public static final String GROUP_BY_INPUT_VARIABLE_TYPE = "inputVariable";
  public static final String GROUP_BY_OUTPUT_VARIABLE_TYPE = "outputVariable";
  public static final String GROUP_BY_MATCHED_RULE_TYPE = "matchedRule";

  public static final String DATE_UNIT_YEAR = "year";
  public static final String DATE_UNIT_MONTH = "month";
  public static final String DATE_UNIT_WEEK = "week";
  public static final String DATE_UNIT_DAY = "day";
  public static final String DATE_UNIT_HOUR = "hour";
  public static final String DATE_UNIT_MINUTE = "minute";
  public static final String DATE_UNIT_AUTOMATIC = "automatic";

  // report configuration constants
  public static final String TABLE_VISUALIZATION = "table";
  public static final String HEAT_VISUALIZATION = "heat";
  public static final String SINGLE_NUMBER_VISUALIZATION = "number";
  public static final String BAR_VISUALIZATION = "bar";
  public static final String LINE_VISUALIZATION = "line";
  public static final String BADGE_VISUALIZATION = "badge";
  public static final String PIE_VISUALIZATION = "pie";

  public static final String DEFAULT_CONFIGURATION_COLOR = "#1991c8";

  public static final String AVERAGE_AGGREGATION_TYPE = "avg";
  public static final String MIN_AGGREGATION_TYPE = "min";
  public static final String MAX_AGGREGATION_TYPE = "max";
  public static final String MEDIAN_AGGREGATION_TYPE = "median";

  // miscellaneous report constants
  public static final String ALL_VERSIONS = "ALL";

  public static final String FIXED_DATE_FILTER = "fixed";
  public static final String RELATIVE_DATE_FILTER = "relative";

  public static final String PROCESS_REPORT_TYPE = "process";
  public static final String DECISION_REPORT_TYPE = "decision";

  public static final String RAW_RESULT_TYPE = "raw";
  public static final String FREQUENCY_MAP_RESULT_TYPE = "frequencyMap";
  public static final String DURATION_MAP_RESULT_TYPE = "durationMap";
  public static final String FREQUENCY_NUMBER_RESULT_TYPE = "frequencyNumber";
  public static final String DURATION_NUMBER_RESULT_TYPE = "durationNumber";
}
