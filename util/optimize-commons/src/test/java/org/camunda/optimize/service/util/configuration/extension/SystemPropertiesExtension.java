/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.extension;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Properties;

import static java.lang.System.getProperties;
import static java.lang.System.setProperties;

/**
 * Extension to allow manipulation of System Properties and restore their state after test completion
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
