/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import org.slf4j.Logger;

public class MockContext implements Context {

  private Logger logger;
  private Configuration configuration;
  private RecordFilter filter;

  public MockContext() {}

  public MockContext(final Logger logger, final Configuration configuration) {
    this.logger = logger;
    this.configuration = configuration;
  }

  @Override
  public Logger getLogger() {
    return logger;
  }

  public void setLogger(final Logger logger) {
    this.logger = logger;
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final Configuration configuration) {
    this.configuration = configuration;
  }

  public RecordFilter getFilter() {
    return filter;
  }

  @Override
  public void setFilter(final RecordFilter filter) {
    this.filter = filter;
  }
}
