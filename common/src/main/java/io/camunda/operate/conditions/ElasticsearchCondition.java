/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

public class ElasticsearchCondition extends DatabaseCondition{

  public static final DatabaseType DATABASE = DatabaseType.Elasticsearch;
  @Override
  public DatabaseType getDatabase() {
    return DATABASE;
  }
}
