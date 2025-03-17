/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.conditions;

import static io.camunda.operate.conditions.DatabaseCondition.DATABASE_PROPERTY;

import io.camunda.search.connect.configuration.DatabaseType;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component("databaseInfo")
public class DatabaseInfo implements ApplicationContextAware, DisposableBean {

  static final DatabaseType DEFAULT_DATABASE = DatabaseType.ELASTICSEARCH;
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInfo.class);
  private static ApplicationContext applicationContext;
  private static DatabaseType current;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "LI_LAZY_INIT_STATIC",
      justification = "We want to avoid the fight for a lock on this method.")
  public static DatabaseType getCurrent() {
    if (current == null) {
      if (applicationContext == null) {
        LOGGER.warn("getCurrent() called on DatabaseInfo before application context has been set");
        return DEFAULT_DATABASE;
      }
      final var code = applicationContext.getEnvironment().getProperty(DATABASE_PROPERTY);
      current = Optional.of(DatabaseType.valueOf(code)).orElse(DEFAULT_DATABASE);
    }
    return current;
  }

  public static boolean isCurrent(final DatabaseType databaseType) {
    return databaseType == getCurrent();
  }

  public static boolean isElasticsearch() {
    return isCurrent(DatabaseType.ELASTICSEARCH);
  }

  public static boolean isOpensearch() {
    return isCurrent(DatabaseType.OPENSEARCH);
  }

  // Helper methods that allow the component to be autowired and safely check the db type instead of
  // using static methods
  public boolean isOpensearchDb() {
    return isOpensearch();
  }

  public boolean isElasticsearchDb() {
    return isElasticsearch();
  }

  @Override
  public void destroy() {
    // unset current so it can be reinitialized
    current = null;
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    DatabaseInfo.applicationContext = applicationContext;
  }
}
