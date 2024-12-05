/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.usermanagement.index;

import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.descriptors.usermanagement.UserManagementIndexDescriptor;

public class MappingIndex extends UserManagementIndexDescriptor implements Prio5Backup {
  public static final String INDEX_NAME = "mapping";
  public static final String INDEX_VERSION = "8.7.0";

  public static final String MAPPING_KEY = "mappingKey";
  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";

  public MappingIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}