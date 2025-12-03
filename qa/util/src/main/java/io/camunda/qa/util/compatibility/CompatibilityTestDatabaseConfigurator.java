/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.compatibility;

import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;

/**
 * Helper class to configure Camunda container environment variables for different database types.
 * This mimics the behavior of MultiDbConfigurator but for containerized deployments.
 */
public class CompatibilityTestDatabaseConfigurator {

  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";

  private final String testPrefix;
  private final DatabaseType databaseType;
  private final String databaseUrl;

  public CompatibilityTestDatabaseConfigurator(
      final String testPrefix, final DatabaseType databaseType, final String databaseUrl) {
    this.testPrefix = testPrefix;
    this.databaseType = databaseType;
    this.databaseUrl = databaseUrl;
  }

  public void configureCamundaContainer(final GenericContainer<?> camundaContainer) {
    final Map<String, String> env = new HashMap<>();

    switch (databaseType) {
      case LOCAL, ES -> configureElasticsearch(env);
      default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }

    // Apply all environment variables
    env.forEach(camundaContainer::withEnv);
  }

  private void configureElasticsearch(final Map<String, String> env) {
    final String zeebePrefix = zeebeIndexPrefix();

    /* Tasklist */
    env.put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", zeebePrefix);

    /* Operate */
    env.put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", zeebePrefix);

    // indexPrefix
    env.put("CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_INDEX_PREFIX", testPrefix);

    // db type
    env.put("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH);
    env.put("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", DB_TYPE_ELASTICSEARCH);
    env.put("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH);
    env.put("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH);

    // url
    env.put("CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL", databaseUrl);
    env.put("CAMUNDA_DATABASE_URL", databaseUrl);
    env.put("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", databaseUrl);
    env.put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", databaseUrl);
    env.put("CAMUNDA_OPERATE_ELASTICSEARCH_URL", databaseUrl);
    env.put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", databaseUrl);

    /* Camunda */
    env.put("CAMUNDA_DATABASE_RETENTION_ENABLED", Boolean.toString(true));
    env.put(
        "CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_HISTORY_POLICY_NAME", testPrefix + "-ilm");
    env.put("CAMUNDA_DATABASE_RETENTION_MINIMUMAGE", "0s");
    env.put("CAMUNDA_DATABASE_SCHEMA_CREATE", Boolean.toString(true));

    /* Unified Config */
    env.put("CAMUNDA_DATA_SECONDARY_STORAGE_RETENTION_ENABLED", Boolean.toString(true));
    env.put("CAMUNDA_DATA_SECONDARY_STORAGE_RETENTION_MINIMUM_AGE", "0s");
  }

  private String zeebeIndexPrefix() {
    return testPrefix != null && !testPrefix.isBlank()
        ? testPrefix + "-" + MultiDbConfigurator.zeebePrefix
        : MultiDbConfigurator.zeebePrefix;
  }
}
