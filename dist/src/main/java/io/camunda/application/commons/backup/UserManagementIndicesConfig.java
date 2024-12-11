/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.profiles.ProfileWebApp;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.MappingIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ProfileWebApp
public class UserManagementIndicesConfig {

  final String prefix;
  final boolean isElasticSearch;

  public UserManagementIndicesConfig(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties) {
    // A map is used for clear error reporting
    // A TreeMap is used to be able to fetch the "first" entry
    final var prefixes = new TreeMap<String, String>();
    final var databases = new TreeMap<String, Boolean>();
    if (operateProperties != null) {
      prefixes.put("operate", operateProperties.getIndexPrefix());
      databases.put("operate", DatabaseInfo.isElasticsearch());
    }
    if (tasklistProperties != null) {
      prefixes.put("tasklist", tasklistProperties.getIndexPrefix());
      databases.put(
          "tasklist", tasklistProperties.getDatabase().equals(TasklistProperties.ELASTIC_SEARCH));
    }

    prefix =
        ConfigValidation.allMatch(
            ErrorMessages.noWebappConfigured,
            ErrorMessages.differentIndexPrefixName,
            prefixes,
            ConfigValidation.skipEmpty);
    isElasticSearch =
        ConfigValidation.allMatch(
            ErrorMessages.noWebappConfigured, ErrorMessages.differentDatabaseConfigured, databases);
  }

  @Bean
  public AuthorizationIndex authorizationIndex() {
    return new AuthorizationIndex(prefix, isElasticSearch);
  }

  @Bean
  public GroupIndex groupIndex() {
    return new GroupIndex(prefix, isElasticSearch);
  }

  @Bean
  public MappingIndex mappingIndex() {
    return new MappingIndex(prefix, isElasticSearch);
  }

  @Bean
  public PersistentWebSessionIndexDescriptor persistentWebSessionIndex() {
    return new PersistentWebSessionIndexDescriptor(prefix, isElasticSearch);
  }

  @Bean
  public RoleIndex roleIndex() {
    return new RoleIndex(prefix, isElasticSearch);
  }

  @Bean
  public TenantIndex tenantIndex() {
    return new TenantIndex(prefix, isElasticSearch);
  }

  @Bean
  public UserIndex userManagementUserIndex() {
    return new UserIndex(prefix, isElasticSearch);
  }

  // Namespace for error messages
  private static final class ErrorMessages {
    public static String differentIndexPrefixName =
        "Expected the same index prefix in operate, tasklist and optimize: Got %s";

    public static String differentDatabaseConfigured =
        "Expected the same database type in operate, tasklist and optimize: Got %s";

    public static String noWebappConfigured =
        "Expected that at least one webapp is configured. No webapp configured";
  }
}
