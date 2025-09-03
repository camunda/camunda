/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

public class MessageCorrelationColumn {
  public static final String SUBSCRIPTION_KEY = "SUBSCRIPTION_KEY";
  public static final String MESSAGE_KEY = "MESSAGE_KEY";
  public static final String MESSAGE_NAME = "MESSAGE_NAME";
  public static final String CORRELATION_KEY = "CORRELATION_KEY";
  public static final String PROCESS_INSTANCE_KEY = "PROCESS_INSTANCE_KEY";
  public static final String FLOW_NODE_INSTANCE_KEY = "FLOW_NODE_INSTANCE_KEY";
  public static final String FLOW_NODE_ID = "FLOW_NODE_ID";
  public static final String BPMN_PROCESS_ID = "BPMN_PROCESS_ID";
  public static final String PROCESS_DEFINITION_KEY = "PROCESS_DEFINITION_KEY";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String DATE_TIME = "DATE_TIME";
  public static final String PARTITION_ID = "PARTITION_ID";
  public static final String HISTORY_CLEANUP_DATE = "HISTORY_CLEANUP_DATE";
}