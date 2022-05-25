/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.context.Configuration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.jcip.annotations.Immutable;

/**
 * An immutable implementation of {@link Configuration}. Accepts configuration suppliers, passing
 * the arguments map to the supplier. This allows for flexible injection of configuration when
 * testing the exporter.
 *
 * @param <T> the actual configuration type
 */
@Immutable
public final class ExporterTestConfiguration<T> implements Configuration {
  private final String id;
  private final Map<String, Object> arguments;
  private final Function<Map<String, Object>, T> configurationSupplier;

  public ExporterTestConfiguration(final String id, final T configuration) {
    this(id, ignored -> configuration);
  }

  public ExporterTestConfiguration(
      final String id, final Function<Map<String, Object>, T> configurationSupplier) {
    this(id, Collections.emptyMap(), configurationSupplier);
  }

  public ExporterTestConfiguration(
      final String id,
      final Map<String, Object> arguments,
      final Function<Map<String, Object>, T> configurationSupplier) {
    this.id = Objects.requireNonNull(id, "must specify an ID");
    this.arguments = Objects.requireNonNull(arguments, "must specify arguments");
    this.configurationSupplier =
        Objects.requireNonNull(configurationSupplier, "must specific a configurationSupplier");
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public <R> R instantiate(final Class<R> configClass) {
    Objects.requireNonNull(configClass, "must pass a non null configClass");

    final var configuration = configurationSupplier.apply(arguments);
    return configClass.cast(configuration);
  }
}
