/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * TEMPORARY load-test hack — DO NOT MERGE. Helm chart 15.0.0-alpha1 can render the
 * disabled-exporters map as an empty scalar; Spring's YAML loading turns that into an empty-string
 * property, which fails to bind to {@code Map<String, ExporterCfg>} and prevents broker startup.
 * Hiding blank {@code zeebe.broker.exporters} leaves before binding restores the intended "no
 * exporters" semantics (absent key → empty map). Inert when the chart renders correctly. Remove
 * once the chart rendering is fixed.
 */
public final class BlankExportersScrubber implements EnvironmentPostProcessor {

  private static final String KEY = "zeebe.broker.exporters";

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    for (final PropertySource<?> source : environment.getPropertySources()) {
      if (source instanceof final EnumerablePropertySource<?> enumerable
          && enumerable.containsProperty(KEY)) {
        final Object value = enumerable.getProperty(KEY);
        if (value == null || value.toString().isBlank()) {
          environment
              .getPropertySources()
              .replace(source.getName(), new BlankKeyHidingSource(enumerable));
        }
      }
    }
  }

  private static final class BlankKeyHidingSource extends EnumerablePropertySource<Object> {

    private final EnumerablePropertySource<?> delegate;

    private BlankKeyHidingSource(final EnumerablePropertySource<?> delegate) {
      super(delegate.getName(), delegate);
      this.delegate = delegate;
    }

    @Override
    public Object getProperty(final String name) {
      return KEY.equals(name) ? null : delegate.getProperty(name);
    }

    @Override
    public String[] getPropertyNames() {
      return Arrays.stream(delegate.getPropertyNames())
          .filter(name -> !KEY.equals(name))
          .toArray(String[]::new);
    }
  }
}
