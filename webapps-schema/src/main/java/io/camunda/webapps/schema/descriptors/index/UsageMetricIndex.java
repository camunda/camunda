/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import java.util.Optional;

public class UsageMetricIndex extends AbstractIndexDescriptor implements Prio5Backup {
  public static final String INDEX_NAME = "usage-metric";
  public static final String INDEX_VERSION = "8.8.0";

  public static final String ID = "id";
  public static final String EVENT_TIME = "eventTime";
  public static final String EVENT_TYPE = "eventType";
  public static final String EVENT_VALUE = "eventValue";
  public static final String TENANT_ID = "tenantId";
  public static final String PARTITION_ID = "partitionId";

  public UsageMetricIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }
}
