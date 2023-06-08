/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportConstants {

  public static final String VIEW_FLOW_NODE_ENTITY = "flowNode";
  public static final String VIEW_USER_TASK_ENTITY = "userTask";
  public static final String VIEW_PROCESS_INSTANCE_ENTITY = "processInstance";
  public static final String VIEW_VARIABLE_ENTITY = "variable";
  public static final String VIEW_INCIDENT_ENTITY = "incident";

  public static final String VIEW_FREQUENCY_PROPERTY = "frequency";
  public static final String VIEW_DURATION_PROPERTY = "duration";
  public static final String VIEW_PERCENTAGE_PROPERTY = "percentage";
  public static final String VIEW_RAW_DATA_PROPERTY = "rawData";

  public static final String GROUP_BY_FLOW_NODES_TYPE = "flowNodes";
  public static final String GROUP_BY_USER_TASKS_TYPE = "userTasks";
  public static final String GROUP_BY_NONE_TYPE = "none";
  public static final String GROUP_BY_START_DATE_TYPE = "startDate";
  public static final String GROUP_BY_END_DATE_TYPE = "endDate";
  public static final String GROUP_BY_RUNNING_DATE_TYPE = "runningDate";
  public static final String GROUP_BY_VARIABLE_TYPE = "variable";
  public static final String GROUP_BY_ASSIGNEE = "assignee";
  public static final String GROUP_BY_CANDIDATE_GROUP = "candidateGroup";
  public static final String GROUP_BY_DURATION = "duration";

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
  public static final String DATE_UNIT_SECOND = "second";
  public static final String DATE_UNIT_MILLISECOND = "millisecond";
  public static final String DATE_UNIT_AUTOMATIC = "automatic";

  // report configuration constants
  public static final String TABLE_VISUALIZATION = "table";
  public static final String HEAT_VISUALIZATION = "heat";
  public static final String SINGLE_NUMBER_VISUALIZATION = "number";
  public static final String BAR_VISUALIZATION = "bar";
  public static final String LINE_VISUALIZATION = "line";
  public static final String BADGE_VISUALIZATION = "badge";
  public static final String PIE_VISUALIZATION = "pie";
  public static final String BAR_LINE_VISUALIZATION = "barLine";

  public static final String DEFAULT_CONFIGURATION_COLOR = "#1991c8";

  public static final String AVERAGE_AGGREGATION_TYPE = "avg";
  public static final String MIN_AGGREGATION_TYPE = "min";
  public static final String MAX_AGGREGATION_TYPE = "max";
  public static final String SUM_AGGREGATION_TYPE = "sum";
  public static final String PERCENTILE_AGGREGATION_TYPE = "percentile";

  public static final String DISTRIBUTED_BY_NONE = "none";
  public static final String DISTRIBUTED_BY_USER_TASK = "userTask";
  public static final String DISTRIBUTED_BY_FLOW_NODE = "flowNode";
  public static final String DISTRIBUTED_BY_ASSIGNEE = "assignee";
  public static final String DISTRIBUTED_BY_CANDIDATE_GROUP = "candidateGroup";
  public static final String DISTRIBUTED_BY_VARIABLE = "variable";
  public static final String DISTRIBUTED_BY_START_DATE = "startDate";
  public static final String DISTRIBUTED_BY_END_DATE = "endDate";
  public static final String DISTRIBUTED_BY_PROCESS = "process";

  public static final String IDLE_USER_TASK_DURATION_TIME = "idle";
  public static final String WORK_USER_TASK_DURATION_TIME = "work";
  public static final String TOTAL_USER_TASK_DURATION_TIME = "total";

  // alert constants
  public static final String ALERT_THRESHOLD_OPERATOR_GREATER = ">";
  public static final String ALERT_THRESHOLD_OPERATOR_LESS = "<";

  // miscellaneous report constants
  public static final String ALL_VERSIONS = "all";
  public static final String LATEST_VERSION = "latest";

  // tenants
  public static final List<String> DEFAULT_TENANT_IDS = Collections.singletonList(null);

  // filter application level
  public static final String INSTANCE = "instance";
  public static final String VIEW = "view";

  // filter applied to special values
  public static final String APPLIED_TO_ALL_DEFINITIONS = "all";

  // date filter
  public static final String FIXED_DATE_FILTER = "fixed";
  public static final String RELATIVE_DATE_FILTER = "relative";
  public static final String ROLLING_DATE_FILTER = "rolling";

  // result types
  public static final String RAW_RESULT_TYPE = "raw";
  public static final String NUMBER_RESULT_TYPE = "number";
  public static final String MAP_RESULT_TYPE = "map";
  public static final String HYPER_MAP_RESULT_TYPE = "hyperMap";

  public static final String MISSING_VARIABLE_KEY = "missing";

  // variable type constants
  // first letter uppercase is used by VariableFilterDataDto json type info
  public static final String STRING_TYPE = "String";
  public static final String STRING_TYPE_LOWERCASE = "string";
  public static final String INTEGER_TYPE = "Integer";
  public static final String INTEGER_TYPE_LOWERCASE = "integer";
  public static final String SHORT_TYPE = "Short";
  public static final String SHORT_TYPE_LOWERCASE = "short";
  public static final String LONG_TYPE = "Long";
  public static final String LONG_TYPE_LOWERCASE = "long";
  public static final String DOUBLE_TYPE = "Double";
  public static final String DOUBLE_TYPE_LOWERCASE = "double";
  public static final String BOOLEAN_TYPE = "Boolean";
  public static final String BOOLEAN_TYPE_LOWERCASE = "boolean";
  public static final String DATE_TYPE = "Date";
  public static final String DATE_TYPE_LOWERCASE = "date";
  public static final String OBJECT_TYPE = "Object";
  public static final String JSON_TYPE = "Json";

  public static final String GROUP_NONE_KEY = "____none";

  public static final List<VariableType> ALL_PRIMITIVE_PROCESS_VARIABLE_TYPES = List.of(
    VariableType.STRING,
    VariableType.SHORT,
    VariableType.LONG,
    VariableType.DOUBLE,
    VariableType.INTEGER,
    VariableType.BOOLEAN,
    VariableType.DATE
  );

  public static final List<VariableType> ALL_SUPPORTED_PROCESS_VARIABLE_TYPES =
    ImmutableList.copyOf(VariableType.values());

  public static final List<VariableType> ALL_SUPPORTED_DECISION_VARIABLE_TYPES = List.of(
    VariableType.STRING,
    VariableType.SHORT,
    VariableType.LONG,
    VariableType.DOUBLE,
    VariableType.INTEGER,
    VariableType.BOOLEAN,
    VariableType.DATE
  );

  // A report result can have three states in theory for duration reports:
  // * an arbitrary positive value,
  // * zero duration
  // * no data available
  // To differentiate between an activity/process instance took 0ms and no data available the
  // null result indicates that there's no data.
  public static final Double NO_DATA_AVAILABLE_RESULT = null;

  // pagination
  public static final int PAGINATION_DEFAULT_LIMIT = 20;
  public static final int PAGINATION_DEFAULT_OFFSET = 0;
  public static final int PAGINATION_DEFAULT_SCROLL_TIMEOUT = 60;

  public static final String API_IMPORT_OWNER_NAME = "System User";
}
