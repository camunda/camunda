/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;

public enum DecisionReportDataType {
  RAW_DATA,
  COUNT_DEC_INST_FREQ_GROUP_BY_NONE,
  COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME,
  COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE,
  COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE,
  COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE,
  ;
}
