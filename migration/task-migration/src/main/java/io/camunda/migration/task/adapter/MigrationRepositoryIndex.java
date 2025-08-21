/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;

/* Fork of Tasklist's MigrationRepositoryIndex descriptor for correct prefixing */
/* See https://github.com/camunda/camunda/blob/stable/8.7/tasklist/els-schema/src/main/java/io/camunda/tasklist/schema/indices/MigrationRepositoryIndex.java */
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
    return TASK_LIST.toString();
  }
}
