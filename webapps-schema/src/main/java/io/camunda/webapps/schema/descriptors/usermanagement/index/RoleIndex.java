/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.usermanagement.index;

import io.camunda.webapps.schema.descriptors.usermanagement.RoleIndexDescriptor;

public class RoleIndex extends RoleIndexDescriptor {
  public static final String INDEX_NAME = "role";
  public static final String INDEX_VERSION = "8.7.0";

  public static final String ROLEKEY = "roleKey";
  public static final String NAME = "name";
  public static final String ASSIGNEDMEMBERKEYS = "assignedMemberKeys";

  public RoleIndex(final String indexPrefix, final boolean isElasticsearch) {
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
