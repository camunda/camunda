/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.conditions;

import java.util.Arrays;
import java.util.Optional;

public enum DatabaseType {
  Elasticsearch("elasticsearch"),
  Opensearch("opensearch");

  private final String code;

  DatabaseType(final String code) {
    this.code = code;
  }

  public static Optional<DatabaseType> byCode(final String code) {
    return Arrays.stream(values()).filter(dt -> dt.code.equals(code)).findFirst();
  }

  public String getCode() {
    return code;
  }
}
