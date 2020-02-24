/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig;

import io.zeebe.legacy.tomlconfig.util.Loggers;

public class LegacySupportTomlConfiguration {

  public static void checkForLegacyTomlConfigurationArgument(
      String[] args, String recommendedSetting) {
    if (args.length == 1 && args[0].endsWith(".toml")) {
      final String configFileArgument = args[0];
      Loggers.LEGACY_LOGGER.warn(
          "Found command line argument "
              + configFileArgument
              + " which might be a TOML configuration file.");
      Loggers.LEGACY_LOGGER.info(
          "TOML configuration files are no longer supported. Please specify a YAML configuration file"
              + "and set it via environment variable \"spring.config.additional-location\" (e.g. "
              + "\"export spring.config.additional-location='file:./config/"
              + recommendedSetting
              + "'\").");
      Loggers.LEGACY_LOGGER.info(
          "The ./config/ folder contains a configuration file template. Alternatively, you can also use environment variables.");
    }
  }
}
