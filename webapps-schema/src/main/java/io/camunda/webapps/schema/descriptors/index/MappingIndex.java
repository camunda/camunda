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

public class MappingIndex extends AbstractIndexDescriptor implements Prio5Backup {
  public static final String INDEX_NAME = "mapping";
  public static final String INDEX_VERSION = "8.8.0";

  public static final String MAPPING_KEY = "mappingRuleKey";
  public static final String MAPPING_ID = "mappingRuleId";
  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";
  public static final String NAME = "name";

  public MappingIndex(final String indexPrefix, final boolean isElasticsearch) {
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
}
