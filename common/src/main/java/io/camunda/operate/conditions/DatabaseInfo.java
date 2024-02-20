/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
public class DatabaseInfo implements ApplicationContextAware {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseInfo.class);
  private static ApplicationContext applicationContext;

  static final DatabaseType DEFAULT_DATABASE = DatabaseType.Elasticsearch;

  public static DatabaseType getCurrent() {
    if (applicationContext == null) {
      logger.warn("getCurrent() called on DatabaseInfo before application context has been set");
      return DEFAULT_DATABASE;
    }

    var code = applicationContext.getEnvironment().getProperty(DATABASE_PROPERTY);
    return DatabaseType.byCode(code).orElse(DEFAULT_DATABASE);
  }

  public static boolean isCurrent(DatabaseType databaseType) {
    return databaseType == getCurrent();
  }

  public static boolean isElasticsearch() {
    return isCurrent(DatabaseType.Elasticsearch);
  }

  public static boolean isOpensearch() {
    return isCurrent(DatabaseType.Opensearch);
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
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    DatabaseInfo.applicationContext = applicationContext;
  }
}
