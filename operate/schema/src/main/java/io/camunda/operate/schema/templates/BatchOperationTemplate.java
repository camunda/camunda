/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.operate.template.AbstractTemplateDescriptor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
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

  @Autowired private OperateProperties properties;

  public BatchOperationTemplate() {
    super(null, false);
  }

  @PostConstruct
  public void init() {
    indexPrefix = properties.getIndexPrefix(DatabaseInfo.getCurrent());
    isElasticsearch = DatabaseInfo.isElasticsearch();
  }

  @Override
  public String getIndexPrefix() {
    return properties.getIndexPrefix();
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
