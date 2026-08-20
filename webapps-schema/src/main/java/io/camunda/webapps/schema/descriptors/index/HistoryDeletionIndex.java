/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;

public class HistoryDeletionIndex extends AbstractIndexDescriptor implements Prio1Backup {

  public static final String INDEX_NAME = "history-deletion";
  public static final String INDEX_VERSION = "8.9.0";
  public static final String ID = "id";
  public static final String RESOURCE_KEY = "resourceKey";
  public static final String RESOURCE_TYPE = "resourceType";
  public static final String BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PARTITION_ID = "partitionId";

  public HistoryDeletionIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return CAMUNDA.toString();
  }
}
