/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import java.time.OffsetDateTime;

public class UserEntity extends AbstractExporterEntity<UserEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  @BeforeVersion880 private Long key;
  @BeforeVersion880 private String username;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String email;
  @BeforeVersion880 private String password;
  private String externalId;
  private String source;
  private Boolean active;
  private String attributes;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastModifiedAt;

  public Long getKey() {
    return key;
  }

  public UserEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public UserEntity setUsername(final String username) {
    this.username = username;
    return this;
  }

  public String getName() {
    return name;
  }

  public UserEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public UserEntity setEmail(final String email) {
    this.email = email;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(final String password) {
    this.password = password;
    return this;
  }

  public String getSource() {
    return source;
  }

  public UserEntity setSource(final String source) {
    this.source = source;
    return this;
  }

  public String getExternalId() {
    return externalId;
  }

  public UserEntity setExternalId(final String externalId) {
    this.externalId = externalId;
    return this;
  }

  public Boolean getActive() {
    return active;
  }

  public UserEntity setActive(final Boolean active) {
    this.active = active;
    return this;
  }

  /** JSON object serialized as a string; not indexed. */
  public String getAttributes() {
    return attributes;
  }

  public UserEntity setAttributes(final String attributes) {
    this.attributes = attributes;
    return this;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public UserEntity setCreatedAt(final OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public OffsetDateTime getLastModifiedAt() {
    return lastModifiedAt;
  }

  public UserEntity setLastModifiedAt(final OffsetDateTime lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }
}
