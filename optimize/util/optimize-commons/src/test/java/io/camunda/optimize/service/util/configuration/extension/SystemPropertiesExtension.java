/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.extension;

import static java.lang.System.getProperties;
import static java.lang.System.setProperties;

import java.util.Properties;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

/**
 * Extension to allow manipulation of System Properties and restore their state after test
 * completion
 */
public class SystemPropertiesExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(SystemPropertiesExtension.class);
  private Properties originalProperties;

  public SystemPropertiesExtension() {}

  @Override
  public void beforeEach(final ExtensionContext extensionContext) throws Exception {
    originalProperties = getProperties();
    setProperties(copyOf(originalProperties));
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) throws Exception {
    setProperties(originalProperties);
  }

  private Properties copyOf(final Properties source) {
    final Properties copy = new Properties();
    copy.putAll(source);
    return copy;
  }
}
