/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

public class ResourceIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "resource";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String RESOURCE_ID = "resourceId";
  public static final String VERSION = "version";
  public static final String VERSION_TAG = "versionTag";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String RESOURCE = "resource";
  public static final String TENANT_ID = IndexDescriptor.TENANT_ID;
  public static final String DEPLOYMENT_KEY = "deploymentKey";
  public static final String IS_DELETED = "isDeleted";

  public ResourceIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
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
