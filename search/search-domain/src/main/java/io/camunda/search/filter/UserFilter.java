/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record UserFilter(
    Long key,
    List<Operation<String>> usernameOperations,
    List<Operation<String>> nameOperations,
    List<Operation<String>> emailOperations,
    String tenantId,
    String groupId,
    String roleId)
    implements FilterBase {

  public Builder toBuilder() {
    return new Builder()
        .usernameOperations(usernameOperations)
        .nameOperations(nameOperations)
        .emailOperations(emailOperations)
        .tenantId(tenantId)
        .groupId(groupId)
        .roleId(roleId);
  }

  public static final class Builder implements ObjectBuilder<UserFilter> {
    private Long key;
    private List<Operation<String>> usernameOperations;
    private List<Operation<String>> nameOperations;
    private List<Operation<String>> emailOperations;
    private String tenantId;
    private String groupId;
    private String roleId;

    public Builder key(final Long value) {
      key = value;
      return this;
    }

    public Builder usernameOperations(final List<Operation<String>> operations) {
      usernameOperations = addValuesToList(usernameOperations, operations);
      return this;
    }

    public Builder usernames(final Set<String> value) {
      return usernameOperations(FilterUtil.mapDefaultToOperation(new ArrayList<>(value)));
    }

    public Builder usernames(final String value, final String... values) {
      return usernameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder usernameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return usernameOperations(collectValues(operation, operations));
    }

    public Builder nameOperations(final List<Operation<String>> operations) {
      nameOperations = addValuesToList(nameOperations, operations);
      return this;
    }

    public Builder names(final Set<String> value) {
      return nameOperations(FilterUtil.mapDefaultToOperation(new ArrayList<>(value)));
    }

    public Builder names(final String value, final String... values) {
      return nameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder nameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return nameOperations(collectValues(operation, operations));
    }

    public Builder emailOperations(final List<Operation<String>> operations) {
      emailOperations = addValuesToList(emailOperations, operations);
      return this;
    }

    public Builder emails(final Set<String> value) {
      return emailOperations(FilterUtil.mapDefaultToOperation(new ArrayList<>(value)));
    }

    public Builder emails(final String value, final String... values) {
      return emailOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder emailOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return emailOperations(collectValues(operation, operations));
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder groupId(final String value) {
      groupId = value;
      return this;
    }

    public Builder roleId(final String value) {
      roleId = value;
      return this;
    }

    @Override
    public UserFilter build() {
      return new UserFilter(
          key,
          Objects.requireNonNullElse(usernameOperations, Collections.emptyList()),
          nameOperations,
          emailOperations,
          tenantId,
          groupId,
          roleId);
    }
  }
}
