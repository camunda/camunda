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

public class UserEntity extends AbstractExporterEntity<UserEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  @BeforeVersion880 private String id;
  @BeforeVersion880 private Long key;
  @BeforeVersion880 private String username;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String email;
  @BeforeVersion880 private String password;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public UserEntity setId(final String id) {
    this.id = id;
    return this;
  }

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
}
