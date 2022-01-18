/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util.it;

import io.camunda.zeebe.exporter.api.context.Configuration;
import java.util.Collections;
import java.util.Map;

/** A mock implementation of {@link Configuration} providing easy control over all properties. */
public class MockConfiguration<T> implements Configuration {

  private String id;
  private final T configuration;

  /** @param configuration will be returned every time by a call to {{@link #instantiate(Class)}} */
  public MockConfiguration(final T configuration) {
    this.configuration = configuration;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return Collections.emptyMap();
  }

  @Override
  public <R> R instantiate(final Class<R> configClass) {
    if (configuration != null && configClass.isAssignableFrom(configuration.getClass())) {
      return configClass.cast(configuration);
    }

    return null;
  }
}
