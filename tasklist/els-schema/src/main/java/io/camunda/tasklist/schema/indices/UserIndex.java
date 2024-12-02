/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.indices;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.tasklist.TasklistIndexDescriptor;

public class UserIndex extends TasklistIndexDescriptor implements Prio4Backup {

  public static final String ID = "id";
  public static final String USER_ID = "userId";
  public static final String DISPLAY_NAME = "displayName";
  public static final String PASSWORD = "password";

  public static final String ROLES = "roles";
  private static final String INDEX_NAME = "user";
  private static final String INDEX_VERSION = "1.4.0";

  public UserIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  private boolean isElasticsearch(final TasklistProperties tasklistProperties) {
    return ELASTIC_SEARCH.equals(tasklistProperties.getDatabase());
  }

  private String getIndexPrefix(final TasklistProperties tasklistProperties) {
    return isElasticsearch(tasklistProperties)
        ? tasklistProperties.getElasticsearch().getIndexPrefix()
        : tasklistProperties.getOpenSearch().getIndexPrefix();
  }
}
