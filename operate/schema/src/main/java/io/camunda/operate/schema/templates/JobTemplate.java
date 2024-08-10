/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import org.springframework.stereotype.Component;

@Component
public class JobTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "job";

  // Long
  public static final String FLOW_NODE_INSTANCE_ID = "flowNodeInstanceId";
  // String - human readable name
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String TENANT_ID = "tenantId";
  public static final String JOB_TYPE = "type";
  public static final String JOB_WORKER = "worker";
  public static final String RETRIES = "retries";
  public static final String JOB_STATE = "state";
  public static final String ERROR_MESSAGE = "errorMessage";
  public static final String ERROR_CODE = "errorCode";
  public static final String JOB_DEADLINE = "deadline";
  public static final String TIME = "endTime";
  public static final String CUSTOM_HEADERS = "customHeaders";
  public static final String JOB_KIND = "jobKind";
  public static final String LISTENER_EVENT_TYPE = "listenerEventType";
  public static final String JOB_KEY = "key";
  public static final String PARTITION_ID = "partitionId";
  public static final String JOB_FAILED_WITH_RETRIES_LEFT = "jobFailedWithRetriesLeft";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.6.0";
  }
}
