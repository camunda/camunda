/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserEntity(
    Long userKey,
    String username,
    @Nullable String name,
    @Nullable String email,
    @Nullable String password,
    @Nullable String externalId,
    @Nullable Boolean active,
    @Nullable Map<String, Object> attributes,
    @Nullable OffsetDateTime createdAt,
    @Nullable OffsetDateTime lastModifiedAt,
    @Nullable String source) {

  public UserEntity(
      final Long userKey,
      final String username,
      final @Nullable String name,
      final @Nullable String email,
      final @Nullable String password,
      final @Nullable String externalId,
      final @Nullable Boolean active,
      final @Nullable Map<String, Object> attributes,
      final @Nullable OffsetDateTime createdAt,
      final @Nullable OffsetDateTime lastModifiedAt) {
    this(
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
        null);
  }

  public UserEntity(
      final Long userKey,
      final String username,
      final @Nullable String name,
      final @Nullable String email,
      final @Nullable String password,
      final @Nullable String externalId,
      final @Nullable Boolean active,
      final @Nullable Map<String, Object> attributes) {
    this(
        userKey, username, name, email, password, externalId, active, attributes, null, null, null);
  }

  public UserEntity(
      final Long userKey,
      final String username,
      final @Nullable String name,
      final @Nullable String email,
      final @Nullable String password,
      final @Nullable String externalId,
      final @Nullable Boolean active) {
    this(userKey, username, name, email, password, externalId, active, null, null, null, null);
  }

  public UserEntity(
      final Long userKey,
      final String username,
      final @Nullable String name,
      final @Nullable String email,
      final @Nullable String password) {
    this(userKey, username, name, email, password, null, null, null, null, null, null);
  }

  public UserEntity {
    Objects.requireNonNull(userKey, "userKey");
    Objects.requireNonNull(username, "username");
  }
}
