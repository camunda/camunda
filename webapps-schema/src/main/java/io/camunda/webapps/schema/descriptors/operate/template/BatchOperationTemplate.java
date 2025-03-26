/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.operate.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;

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
  public static final String FAILED_OPERATIONS_COUNT = "failedOperationsCount";
  public static final String COMPLETED_OPERATIONS_COUNT = "completedOperationsCount";

  public BatchOperationTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
