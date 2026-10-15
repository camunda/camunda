/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public record UserDbModel(
    Long userKey,
    String username,
    String name,
    String email,
    String password,
    String externalId,
    Boolean active,
    String attributes,
    OffsetDateTime createdAt,
    OffsetDateTime lastModifiedAt,
    String source)
    implements DbModel<UserDbModel> {

  @Override
  public UserDbModel copy(
      final Function<ObjectBuilder<UserDbModel>, ObjectBuilder<UserDbModel>> copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .username(username)
                .name(name)
                .email(email)
                .password(password)
                .externalId(externalId)
                .active(active)
                .attributes(attributes)
                .createdAt(createdAt)
                .lastModifiedAt(lastModifiedAt)
                .source(source))
        .build();
  }

  public static class Builder implements ObjectBuilder<UserDbModel> {

    private Long userKey;
    private String username;
    private String name;
    private String email;
    private String password;
    private String externalId;
    private Boolean active;
    private String attributes;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastModifiedAt;
    private String source;

    public Builder() {}

    // Builder methods for each field
    public Builder userKey(final Long userKey) {
      this.userKey = userKey;
      return this;
    }

    public Builder username(final String username) {
      this.username = username;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder email(final String email) {
      this.email = email;
      return this;
    }

    public Builder password(final String password) {
      this.password = password;
      return this;
    }

    public Builder externalId(final String externalId) {
      this.externalId = externalId;
      return this;
    }

    public Builder active(final Boolean active) {
      this.active = active;
      return this;
    }

    public Builder attributes(final String attributes) {
      this.attributes = attributes;
      return this;
    }

    public Builder createdAt(final OffsetDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder lastModifiedAt(final OffsetDateTime lastModifiedAt) {
      this.lastModifiedAt = lastModifiedAt;
      return this;
    }

    public Builder source(final String source) {
      this.source = source;
      return this;
    }

    // Build method to create the record
    @Override
    public UserDbModel build() {
      return new UserDbModel(
          userKey,
          username,
          name,
          email,
          password,
          externalId,
          active,
          attributes,
          createdAt,
          lastModifiedAt,
          source);
    }
  }
}
