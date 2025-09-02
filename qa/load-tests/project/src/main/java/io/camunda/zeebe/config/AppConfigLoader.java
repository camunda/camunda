/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppConfigLoader {
  private static final Logger LOG = LoggerFactory.getLogger(AppConfigLoader.class);

  private AppConfigLoader() {}

  public static AppCfg load() {
    final Config config = ConfigFactory.load().getConfig("app");
    LOG.info("Loading config: {}", config.root().render());
    return ConfigBeanFactory.create(config, AppCfg.class);
  }

  public static AppCfg load(final String path) {
    final Config config = ConfigFactory.load(path).getConfig("app");
    LOG.info("Loading config: {}", config.root().render());
    return ConfigBeanFactory.create(config, AppCfg.class);
  }
}
