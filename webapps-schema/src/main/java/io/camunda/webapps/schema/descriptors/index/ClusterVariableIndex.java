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
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import java.util.Optional;

public class ClusterVariableIndex extends AbstractIndexDescriptor implements Prio5Backup {

  public static final String INDEX_NAME = "cluster-variable";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String FULL_VALUE = "fullValue";
  public static final String SCOPE = "scope";
  public static final String TENANT_ID = "tenantId";
  public static final String IS_PREVIEW = "isPreview";

  public ClusterVariableIndex(final String indexPrefix, final boolean isElasticsearch) {
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
    return CAMUNDA.toString();
  }
}
