/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;

public class EnvironmentHelper {

  public static boolean isProductionEnvironment(Environment springEnvironment) {
    boolean result = true;

    if (springEnvironment == null) {
      result = false;
    } else {
      final String[] activeProfiles = springEnvironment.getActiveProfiles();
      if (activeProfiles != null && !(activeProfiles.length == 0)) {
        final List<String> activeProfileList = Arrays.asList(activeProfiles);

        if (activeProfileList.contains("dev") || activeProfileList.contains("test")) {
          result = false;
        }
      }
    }

    return result;
  }
}
