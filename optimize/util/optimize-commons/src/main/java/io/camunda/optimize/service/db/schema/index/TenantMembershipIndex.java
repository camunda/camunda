/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * POC: assigns a member (user, group or client) to a logical tenant. Optimize owns this data once
 * Management Identity is gone, so it is deliberately separate from the Camunda 7 {@code tenant}
 * index. The document id is {@code tenantId:memberType:memberId}, which makes assignment
 * idempotent.
 */
public abstract class TenantMembershipIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String TENANT_ID = "tenantId";
  public static final String MEMBER_TYPE = "memberType";
  public static final String MEMBER_ID = "memberId";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(MEMBER_TYPE, p -> p.keyword(k -> k))
        .properties(MEMBER_ID, p -> p.keyword(k -> k));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.TENANT_MEMBERSHIP_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
