/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

import java.util.Arrays;
import java.util.Optional;

public enum DatabaseType {
  Elasticsearch("elasticsearch"),
  Opensearch("opensearch");

  private final String code;

  DatabaseType(String code) {
    this.code = code;
  }

  public static Optional<DatabaseType> byCode(String code) {
    return Arrays.stream(values()).filter(dt -> dt.code.equals(code)).findFirst();
  }

  public String getCode() {
    return code;
  }
}
