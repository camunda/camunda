/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe;

import io.zeebe.broker.Loggers;
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

  public static void checkForLegacyTomlConfigurationArgument(
      String[] args, String recommendedSetting) {
    if (args.length == 1 && args[0].endsWith(".toml")) {
      final String configFileArgument = args[0];
      Loggers.SYSTEM_LOGGER.warn(
          "Found command line argument "
              + configFileArgument
              + " which might be a TOML configuration file.");
      Loggers.SYSTEM_LOGGER.info(
          "TOML configuration files are no longer supported. Please specify a YAML configuration file"
              + "and set it via environment variable \"spring.config.additional-location\" (e.g. "
              + "\"export spring.config.additional-location='file:./config/"
              + recommendedSetting
              + "'\").");
      Loggers.SYSTEM_LOGGER.info(
          "The ./config/ folder contains a configuration file template. Alternatively, you can also use environment variables.");
    }
  }
}
