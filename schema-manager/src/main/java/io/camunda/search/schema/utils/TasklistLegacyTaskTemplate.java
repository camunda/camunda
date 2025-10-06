/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import io.camunda.webapps.schema.descriptors.template.TaskTemplate;

/**
 * Legacy Tasklist task template with version 8.5.0. Only required to verify whether a cluster is a
 * brownfield or a greenfield installation.
 */
public class TasklistLegacyTaskTemplate extends TaskTemplate {

  public TasklistLegacyTaskTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return "8.5.0";
  }
}
