/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import io.zeebe.protocol.record.Record;

public abstract class IdUtil {

  public static String getId(long key, Record record) {
    return String.valueOf(key);
  }

  public static String getId(Record record) {
    return String.valueOf(record.getKey());
  }

  public static long getKey(String id) {
    return Long.valueOf(id);
  }

  public static String getVariableId(long scopeKey, String name) {
    return String.format("%s-%s", scopeKey, name);
  }

}
