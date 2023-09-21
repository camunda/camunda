/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

public class DatabaseInfo {
  static final String DATABASE_PROPERTY = "camunda.operate.database";
  static final DatabaseType DEFAULT_DATABASE = DatabaseType.Elasticsearch;
  static final DatabaseType DATABASE = DatabaseType.byCode(System.getenv(DATABASE_PROPERTY)).orElse(DEFAULT_DATABASE);

  public static DatabaseType getCurrent(){
    return DATABASE;
  }
  public static boolean isCurrent(DatabaseType databaseType){
    return DATABASE == databaseType;
  }

  public static boolean isElasticsearch(){
    return isCurrent(DatabaseType.Elasticsearch);
  }

  public static boolean isOpensearch(){
    return isCurrent(DatabaseType.Opensearch);
  }
}
