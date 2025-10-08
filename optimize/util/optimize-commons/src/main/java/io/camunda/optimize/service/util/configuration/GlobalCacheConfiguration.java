/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class GlobalCacheConfiguration {

  private CacheConfiguration definitions;
  private CacheConfiguration definitionEngines;
  private CloudUserCacheConfiguration cloudUsers;
  private CacheConfiguration cloudTenantAuthorizations;
  private CacheConfiguration users;

  public GlobalCacheConfiguration() {}

  public CacheConfiguration getDefinitions() {
    return definitions;
  }

  public void setDefinitions(final CacheConfiguration definitions) {
    this.definitions = definitions;
  }

  public CacheConfiguration getDefinitionEngines() {
    return definitionEngines;
  }

  public void setDefinitionEngines(final CacheConfiguration definitionEngines) {
    this.definitionEngines = definitionEngines;
  }

  public CloudUserCacheConfiguration getCloudUsers() {
    return cloudUsers;
  }

  public void setCloudUsers(final CloudUserCacheConfiguration cloudUsers) {
    this.cloudUsers = cloudUsers;
  }

  public CacheConfiguration getCloudTenantAuthorizations() {
    return cloudTenantAuthorizations;
  }

  public void setCloudTenantAuthorizations(final CacheConfiguration cloudTenantAuthorizations) {
    this.cloudTenantAuthorizations = cloudTenantAuthorizations;
  }

  public CacheConfiguration getUsers() {
    return users;
  }

  public void setUsers(final CacheConfiguration users) {
    this.users = users;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof GlobalCacheConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GlobalCacheConfiguration that = (GlobalCacheConfiguration) o;
    return Objects.equals(definitions, that.definitions)
        && Objects.equals(definitionEngines, that.definitionEngines)
        && Objects.equals(cloudUsers, that.cloudUsers)
        && Objects.equals(cloudTenantAuthorizations, that.cloudTenantAuthorizations)
        && Objects.equals(users, that.users);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        definitions, definitionEngines, cloudUsers, cloudTenantAuthorizations, users);
  }

  @Override
  public String toString() {
    return "GlobalCacheConfiguration(definitions="
        + getDefinitions()
        + ", definitionEngines="
        + getDefinitionEngines()
        + ", cloudUsers="
        + getCloudUsers()
        + ", cloudTenantAuthorizations="
        + getCloudTenantAuthorizations()
        + ", users="
        + getUsers()
        + ")";
  }
}
