/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class GlobalCacheConfiguration {

  private CacheConfiguration tenants;
  private CacheConfiguration definitions;
  private CacheConfiguration definitionEngines;
  private CloudUserCacheConfiguration cloudUsers;
  private CacheConfiguration cloudTenantAuthorizations;
  private CacheConfiguration users;

  public GlobalCacheConfiguration() {}

  public CacheConfiguration getTenants() {
    return tenants;
  }

  public void setTenants(final CacheConfiguration tenants) {
    this.tenants = tenants;
  }

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
    final int PRIME = 59;
    int result = 1;
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    final Object $definitions = getDefinitions();
    result = result * PRIME + ($definitions == null ? 43 : $definitions.hashCode());
    final Object $definitionEngines = getDefinitionEngines();
    result = result * PRIME + ($definitionEngines == null ? 43 : $definitionEngines.hashCode());
    final Object $cloudUsers = getCloudUsers();
    result = result * PRIME + ($cloudUsers == null ? 43 : $cloudUsers.hashCode());
    final Object $cloudTenantAuthorizations = getCloudTenantAuthorizations();
    result =
        result * PRIME
            + ($cloudTenantAuthorizations == null ? 43 : $cloudTenantAuthorizations.hashCode());
    final Object $users = getUsers();
    result = result * PRIME + ($users == null ? 43 : $users.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GlobalCacheConfiguration)) {
      return false;
    }
    final GlobalCacheConfiguration other = (GlobalCacheConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$tenants = getTenants();
    final Object other$tenants = other.getTenants();
    if (this$tenants == null ? other$tenants != null : !this$tenants.equals(other$tenants)) {
      return false;
    }
    final Object this$definitions = getDefinitions();
    final Object other$definitions = other.getDefinitions();
    if (this$definitions == null
        ? other$definitions != null
        : !this$definitions.equals(other$definitions)) {
      return false;
    }
    final Object this$definitionEngines = getDefinitionEngines();
    final Object other$definitionEngines = other.getDefinitionEngines();
    if (this$definitionEngines == null
        ? other$definitionEngines != null
        : !this$definitionEngines.equals(other$definitionEngines)) {
      return false;
    }
    final Object this$cloudUsers = getCloudUsers();
    final Object other$cloudUsers = other.getCloudUsers();
    if (this$cloudUsers == null
        ? other$cloudUsers != null
        : !this$cloudUsers.equals(other$cloudUsers)) {
      return false;
    }
    final Object this$cloudTenantAuthorizations = getCloudTenantAuthorizations();
    final Object other$cloudTenantAuthorizations = other.getCloudTenantAuthorizations();
    if (this$cloudTenantAuthorizations == null
        ? other$cloudTenantAuthorizations != null
        : !this$cloudTenantAuthorizations.equals(other$cloudTenantAuthorizations)) {
      return false;
    }
    final Object this$users = getUsers();
    final Object other$users = other.getUsers();
    if (this$users == null ? other$users != null : !this$users.equals(other$users)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "GlobalCacheConfiguration(tenants="
        + getTenants()
        + ", definitions="
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
