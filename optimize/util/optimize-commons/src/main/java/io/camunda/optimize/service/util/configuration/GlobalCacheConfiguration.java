/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
