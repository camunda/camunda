/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;

public class BatchOperationTemplate extends AbstractTemplateDescriptor implements Prio3Backup {

  public static final String INDEX_NAME = "batch-operation";

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String NAME = "name";
  public static final String USERNAME = "username";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String INSTANCES_COUNT = "instancesCount";
  public static final String OPERATIONS_TOTAL_COUNT = "operationsTotalCount";
  public static final String OPERATIONS_FINISHED_COUNT = "operationsFinishedCount";

  public BatchOperationTemplate(String indexPrefix) {
    super(indexPrefix);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
