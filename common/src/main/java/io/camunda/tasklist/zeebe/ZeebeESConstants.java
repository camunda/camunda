/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebe;

@SuppressWarnings("checkstyle:InterfaceIsType")
public interface ZeebeESConstants {

  String POSITION_FIELD_NAME = "position";
  String SEQUENCE_FIELD_NAME = "sequence";
  String PROCESS_INSTANCE_INDEX_NAME = "process-instance";
  String JOB_INDEX_NAME = "job";
  String PROCESS_INDEX_NAME = "process";
  String VARIABLE_INDEX_NAME = "variable";
  String VARIABLE_DOCUMENT_INDEX_NAME = "variable-document";
}
