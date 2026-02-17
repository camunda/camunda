/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import io.camunda.zeebe.protocol.record.value.GlobalListenerType;

public class GlobalListenerUtil {
  public static String generateId(final String id, final GlobalListenerType listenerType) {
    return String.format("%s-%s", listenerType.name(), id);
  }

  public static String generateId(
      final String id, final io.camunda.search.entities.GlobalListenerType listenerType) {
    return String.format("%s-%s", listenerType.name(), id);
  }
}
