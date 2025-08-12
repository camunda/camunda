/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import java.util.Optional;

public class TaskLegacyIndex extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "task";
  public static final String INDEX_VERSION = "8.5.0";

  public TaskLegacyIndex(final String indexPrefix, final boolean isElasticsearch) {
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
    return TASK_LIST.toString();
  }
}
