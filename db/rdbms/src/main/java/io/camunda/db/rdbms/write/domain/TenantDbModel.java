/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public class TenantDbModel implements DbModel<TenantDbModel> {

  private Long tenantKey;
  private String tenantId;
  private String name;
  private String description;
  private List<TenantMemberDbModel> members;

  public TenantDbModel() {}

  private TenantDbModel(
      final String tenantId,
      final Long tenantKey,
      final String name,
      final String description,
      final List<TenantMemberDbModel> members) {
    this.tenantKey = tenantKey;
    this.tenantId = tenantId;
    this.name = name;
    this.description = description;
    this.members = members;
  }

  @Override
  public TenantDbModel copy(
      final Function<ObjectBuilder<TenantDbModel>, ObjectBuilder<TenantDbModel>> builderFunction) {
    return builderFunction
        .apply(
            new Builder()
                .tenantKey(tenantKey)
                .tenantId(tenantId)
                .name(name)
                .description(description))
        .build();
  }

  public Long tenantKey() {
    return tenantKey;
  }

  public void tenantKey(final Long tenantKey) {
    this.tenantKey = tenantKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String name() {
    return name;
  }

  public void name(final String name) {
    this.name = name;
  }

  public String description() {
    return description;
  }

  public void description(final String description) {
    this.description = description;
  }

  public List<TenantMemberDbModel> members() {
    return members;
  }

  public void members(final List<TenantMemberDbModel> members) {
    this.members = members;
  }

  public static class Builder implements ObjectBuilder<TenantDbModel> {

    private Long tenantKey;
    private String tenantId;
    private String name;
    private String description;
    private List<TenantMemberDbModel> members;

    // Public constructor to initialize the builder
    public Builder() {}

    // Builder methods for each field
    public Builder tenantKey(final Long tenantKey) {
      this.tenantKey = tenantKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    public Builder members(final List<TenantMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public TenantDbModel build() {
      return new TenantDbModel(tenantId, tenantKey, name, description, members);
    }
  }
}
