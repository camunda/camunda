/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import java.util.HashSet;
import java.util.Set;
import org.springframework.core.env.ConfigurableEnvironment;

public class PropertiesHelper {

  public static Set<String> loadListProperty(
      final ConfigurableEnvironment environment, final String groupName) {
    final Set<String> result = new HashSet<>();
    final String[] currentGroup = environment.getProperty(groupName, "").split(",");
    for (final String element : currentGroup) {
      if (element == null) {
        continue;
      }

      final String trimmedElement = element.trim();
      if (trimmedElement.isEmpty()) {
        continue;
      }

      result.add(trimmedElement);
    }
    return result;
  }
}
