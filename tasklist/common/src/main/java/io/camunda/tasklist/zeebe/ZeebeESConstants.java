/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  String FORM_INDEX_NAME = "form";
  String USER_TASK_INDEX_NAME = "user-task";
  String DEPLOYMENT = "deployment";
}
