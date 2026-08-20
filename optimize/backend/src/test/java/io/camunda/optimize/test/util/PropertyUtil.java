/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PropertyUtil.class);

  public static Properties loadProperties(final String resource) {
    final Properties properties = new Properties();
    try {
      properties.load(PropertyUtil.class.getClassLoader().getResourceAsStream(resource));
    } catch (final IOException ex) {
      LOG.error("Unable to load test properties!", ex);
    }
    return properties;
  }
}
