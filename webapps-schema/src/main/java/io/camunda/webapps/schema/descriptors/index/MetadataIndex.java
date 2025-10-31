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
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;

public final class MetadataIndex extends AbstractIndexDescriptor implements Prio1Backup {

  public static final String INDEX_NAME = "metadata";

  public static final String ID = "id";

  public static final String VALUE = "value";

  public MetadataIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.8.0";
  }

  @Override
  public String getComponentName() {
    return OPERATE
        .toString(); // using Operate component since we need to backport this index to 8.7 and 8.6
  }
}
