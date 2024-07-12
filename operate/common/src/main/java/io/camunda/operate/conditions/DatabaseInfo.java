/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.conditions;

import static io.camunda.operate.conditions.DatabaseCondition.DATABASE_PROPERTY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component("databaseInfo")
public class DatabaseInfo implements ApplicationContextAware, DatabaseInfoProvider {

  static final DatabaseType DEFAULT_DATABASE = DatabaseType.Elasticsearch;
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInfo.class);
  private static ApplicationContext applicationContext;

  private static DatabaseType getCurrentStatic() {
    if (applicationContext == null) {
      LOGGER.warn("getCurrent() called on DatabaseInfo before application context has been set");
      return DEFAULT_DATABASE;
    }

    final var code = applicationContext.getEnvironment().getProperty(DATABASE_PROPERTY);
    return DatabaseType.byCode(code).orElse(DEFAULT_DATABASE);
  }

  @Override
  public DatabaseType getCurrent() {
    if (applicationContext == null) {
      LOGGER.warn("getCurrent() called on DatabaseInfo before application context has been set");
      return DEFAULT_DATABASE;
    }

    final var code = applicationContext.getEnvironment().getProperty(DATABASE_PROPERTY);
    return DatabaseType.byCode(code).orElse(DEFAULT_DATABASE);
  }

  @Override
  public boolean isElasticsearch() {
    return isCurrent(DatabaseType.Elasticsearch);
  }

  // Helper methods that allow the component to be autowired and safely check the db type instead of
  // using static methods
  @Override
  public boolean isOpensearch() {
    return isCurrent(DatabaseType.Opensearch);
  }

  private static boolean isCurrent(final DatabaseType databaseType) {
    return databaseType == getCurrentStatic();
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    DatabaseInfo.applicationContext = applicationContext;
  }
}
