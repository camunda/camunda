/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;

/* Fork of Operate's MigrationRepositoryIndex descriptor for correct prefixing */
public class MigrationRepositoryIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "migration-steps-repository";
  public static final String VERSION = "1.1.0";
  public static final String TYPE = "@type";
  public static final String ID = "_id";

  public MigrationRepositoryIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return VERSION;
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
