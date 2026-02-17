/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import java.util.Optional;

public class JobMetricsBatchTemplate extends AbstractTemplateDescriptor implements Prio5Backup {

  public static final String INDEX_NAME = "job-metrics-batch";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String PARTITION_ID = "partitionId";
  public static final String START_TIME = "startTime";
  public static final String END_TIME = "endTime";
  public static final String INCOMPLETE_BATCH = "incompleteBatch";
  public static final String TENANT_ID = "tenantId";
  public static final String FAILED_COUNT = "failedCount";
  public static final String LAST_FAILED_AT = "lastFailedAt";
  public static final String COMPLETED_COUNT = "completedCount";
  public static final String LAST_COMPLETED_AT = "lastCompletedAt";
  public static final String CREATED_COUNT = "createdCount";
  public static final String LAST_CREATED_AT = "lastCreatedAt";
  public static final String JOB_TYPE = "jobType";
  public static final String WORKER = "worker";

  public JobMetricsBatchTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }
}
