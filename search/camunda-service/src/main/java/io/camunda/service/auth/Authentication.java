/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.auth;

import static io.camunda.data.clients.query.DataStoreQueryBuilders.stringTerms;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.service.query.filter.FilterBase;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.List;

public class Authentication extends FilterBase {

  private final String authenticatedUserId;
  private final List<String> authenticatedGroupIds;
  private final List<String> authenticatedTenantIds;

  public Authentication(final Builder builder) {
    authenticatedUserId = builder.user;
    authenticatedGroupIds = builder.groups;
    authenticatedTenantIds = builder.tenants;
  }

  public List<String> getGroupIds() {
    return authenticatedGroupIds;
  }

  public String getUserId() {
    return authenticatedUserId;
  }

  public List<String> getTenantIds() {
    return authenticatedTenantIds;
  }

  public DataStoreQuery toSearchQuery() {
    // TODO: handle the cases as necessary
    if (authenticatedTenantIds != null && !authenticatedTenantIds.isEmpty()) {
      return stringTerms("tenantId", authenticatedTenantIds);
    }
    return null;
  }

  public static final class Builder implements DataStoreObjectBuilder<Authentication> {

    private String user;
    private List<String> groups = new ArrayList<String>();
    private List<String> tenants = new ArrayList<String>();

    public Builder user(final String user) {
      this.user = user;
      return this;
    }

    public Builder group(final String group) {
      groups.add(group);
      return this;
    }

    public Builder groups(final List<String> groups) {
      this.groups.addAll(groups);
      return this;
    }

    public Builder tenant(final String tenant) {
      tenants.add(tenant);
      return this;
    }

    public Builder tenants(final List<String> tenants) {
      this.tenants.addAll(tenants);
      return this;
    }

    @Override
    public Authentication build() {
      return new Authentication(this);
    }
  }
}
