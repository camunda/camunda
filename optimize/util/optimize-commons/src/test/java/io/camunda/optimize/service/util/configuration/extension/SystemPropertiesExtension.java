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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extension to allow manipulation of System Properties and restore their state after test
 * completion
 */
@Slf4j
@NoArgsConstructor
public class SystemPropertiesExtension implements BeforeEachCallback, AfterEachCallback {

  private Properties originalProperties;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) throws Exception {
    originalProperties = getProperties();
    setProperties(copyOf(originalProperties));
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) throws Exception {
    setProperties(originalProperties);
  }

  private Properties copyOf(Properties source) {
    Properties copy = new Properties();
    copy.putAll(source);
    return copy;
  }
}
