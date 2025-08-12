/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InitializationConfiguration {

  public static final String DEFAULT_USER_USERNAME = "demo";
  public static final String DEFAULT_USER_PASSWORD = "demo";
  public static final String DEFAULT_USER_NAME = "Demo";
  public static final String DEFAULT_USER_EMAIL = "demo@example.com";

  /** 1 or more alphanumeric characters, '_', '@', '.', '+', or '-'. */
  public static final String DEFAULT_ID_REGEX = "^[a-zA-Z0-9_@.+-]+$";

  private List<ConfiguredUser> users = new ArrayList<>();
  private List<ConfiguredMappingRule> mappingRules = new ArrayList<>();
  private Map<String, Map<String, Collection<String>>> defaultRoles = new HashMap<>();
  private String identifierRegex = DEFAULT_ID_REGEX;
  private Pattern identifierPattern;

  public List<ConfiguredUser> getUsers() {
    return users;
  }

  public void setUsers(final List<ConfiguredUser> users) {
    this.users = users;
  }

  public List<ConfiguredMappingRule> getMappingRules() {
    return mappingRules;
  }

  public void setMappingRules(final List<ConfiguredMappingRule> mappingRules) {
    this.mappingRules = mappingRules;
  }

  public Map<String, Map<String, Collection<String>>> getDefaultRoles() {
    return defaultRoles;
  }

  public void setDefaultRoles(final Map<String, Map<String, Collection<String>>> defaultRoles) {
    this.defaultRoles = defaultRoles;
  }

  public String getIdentifierRegex() {
    return identifierRegex;
  }

  public void setIdentifierRegex(final String identifierRegex) {
    this.identifierRegex = identifierRegex;
  }

  public Pattern getIdentifierPattern() {
    if (identifierPattern == null) {
      identifierPattern = Pattern.compile(identifierRegex);
    }
    return identifierPattern;
  }
}
