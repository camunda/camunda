/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.util.HashSet;
import java.util.Set;

public final class TestTenant {
  private final String id;
  private String name;
  private String description = "";
  private final Set<String> users = new HashSet<>();
  private final Set<String> groups = new HashSet<>();
  private final Set<String> clients = new HashSet<>();
  private final Set<String> roles = new HashSet<>();
  private final Set<String> mappingRules = new HashSet<>();

  public TestTenant(final String id) {
    this.id = id;
    name = id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public TestTenant setName(final String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public TestTenant setDescription(final String description) {
    this.description = description;
    return this;
  }

  public Set<String> getUsers() {
    return users;
  }

  public Set<String> getGroups() {
    return groups;
  }

  public Set<String> getClients() {
    return clients;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public Set<String> getMappingRules() {
    return mappingRules;
  }

  public TestTenant addUsers(final String... users) {
    this.users.addAll(Set.of(users));
    return this;
  }

  public TestTenant addGroups(final String... groups) {
    this.groups.addAll(Set.of(groups));
    return this;
  }

  public TestTenant addClients(final String... clients) {
    this.clients.addAll(Set.of(clients));
    return this;
  }

  public TestTenant addRoles(final String... roles) {
    this.roles.addAll(Set.of(roles));
    return this;
  }

  public TestTenant addMappingRules(final String... mappingRules) {
    this.mappingRules.addAll(Set.of(mappingRules));
    return this;
  }
}
