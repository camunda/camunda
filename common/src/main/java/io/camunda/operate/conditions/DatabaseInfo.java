/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import static io.camunda.operate.conditions.DatabaseCondition.DATABASE_PROPERTY;

@Component("databaseInfo")
public class DatabaseInfo implements ApplicationContextAware {

  private static ApplicationContext applicationContext;

  static final DatabaseType DEFAULT_DATABASE = DatabaseType.Elasticsearch;

  public static DatabaseType getCurrent(){
    var code =  applicationContext.getEnvironment().getProperty(DATABASE_PROPERTY);
    return DatabaseType.byCode(code).orElse(DEFAULT_DATABASE);
  }
  public static boolean isCurrent(DatabaseType databaseType){
    return databaseType == getCurrent();
  }

  public static boolean isElasticsearch(){
    return isCurrent(DatabaseType.Elasticsearch);
  }

  public static boolean isOpensearch(){
    return isCurrent(DatabaseType.Opensearch);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    DatabaseInfo.applicationContext = applicationContext;
  }
}
