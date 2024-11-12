/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.usermanagement.index;

import io.camunda.webapps.schema.descriptors.usermanagement.UserManagementIndexDescriptor;

public class TenantIndex extends UserManagementIndexDescriptor {
  public static final String INDEX_NAME = "tenants";
  public static final String INDEX_VERSION = "8.7.0";

  public static final String TENANT_KEY = "tenantKey";
  public static final String TENANT_ID = "tenantId";
  public static final String NAME = "name";
  public static final String ASSIGNED_MEMBER_KEYS = "assignedMemberKeys";

  public TenantIndex(final String indexPrefix, final boolean isElasticsearch) {
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
