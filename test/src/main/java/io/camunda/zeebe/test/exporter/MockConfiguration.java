/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import io.camunda.zeebe.exporter.api.context.Configuration;
import java.util.Map;

/**
 * A mock implementation of {@link Configuration} providing easy control over all properties.
 *
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockConfiguration<T> implements Configuration {

  private String id;
  private Map<String, Object> arguments;
  private T configuration;

  public MockConfiguration() {}

  /** @param configuration will be returned every time by a call to {{@link #instantiate(Class)}} */
  public MockConfiguration(final T configuration) {
    this.configuration = configuration;
  }

  public MockConfiguration(final String id, final Map<String, Object> arguments) {
    this.id = id;
    this.arguments = arguments;
  }

  public MockConfiguration(
      final String id, final Map<String, Object> arguments, final T configuration) {
    this.id = id;
    this.arguments = arguments;
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
    return arguments;
  }

  public void setArguments(final Map<String, Object> arguments) {
    this.arguments = arguments;
  }

  @Override
  public <R> R instantiate(final Class<R> configClass) {
    if (configuration != null && configClass.isAssignableFrom(configuration.getClass())) {
      return configClass.cast(configuration);
    }

    return null;
  }

  /**
   * Sets a fixed return value for {@link #instantiate(Class)}
   *
   * @param configuration instance to return for {@link #instantiate(Class)} calls
   */
  public void setConfiguration(final T configuration) {
    this.configuration = configuration;
  }
}
