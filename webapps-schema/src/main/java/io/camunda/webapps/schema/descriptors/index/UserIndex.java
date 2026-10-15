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
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;

public class UserIndex extends AbstractIndexDescriptor implements Prio4Backup {
  public static final String INDEX_NAME = "user";
  public static final String INDEX_VERSION = "8.8.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String NAME = "name";
  public static final String USERNAME = "username";
  public static final String EMAIL = "email";
  public static final String PASSWORD = "password";
  public static final String EXTERNAL_ID = "externalId";
  public static final String SOURCE = "source";
  public static final String ACTIVE = "active";
  public static final String ATTRIBUTES = "attributes";
  public static final String CREATED_AT = "createdAt";
  public static final String LAST_MODIFIED_AT = "lastModifiedAt";

  public UserIndex(final String indexPrefix, final boolean isElasticsearch) {
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
