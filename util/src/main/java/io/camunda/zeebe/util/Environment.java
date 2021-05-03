/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

public final class Environment {

  private static final Logger LOG = Loggers.CONFIG_LOGGER;

  private final Map<String, String> environment;

  public Environment() {
    this(System.getenv());
  }

  public Environment(final Map<String, String> environment) {
    this.environment = environment;
  }

  public Set<String> getPropertyKeys() {
    return Collections.unmodifiableSet(environment.keySet());
  }

  public Optional<String> get(final String name) {
    return Optional.ofNullable(environment.get(name));
  }

  public Optional<Integer> getInt(final String name) {
    try {
      return get(name).map(Integer::valueOf);
    } catch (final Exception e) {
      LOG.warn("Failed to parse environment variable {}", name, e);
      return Optional.empty();
    }
  }

  public Optional<Double> getDouble(final String name) {
    try {
      return get(name).map(Double::valueOf);
    } catch (final Exception e) {
      LOG.warn("Failed to parse environment variable {}", name, e);
      return Optional.empty();
    }
  }

  public Optional<Long> getLong(final String name) {
    try {
      return get(name).map(Long::valueOf);
    } catch (final Exception e) {
      LOG.warn("Failed to parse environment variable {}", name, e);
      return Optional.empty();
    }
  }

  public Optional<Boolean> getBool(final String name) {
    try {
      return get(name).map(Boolean::valueOf);
    } catch (final Exception e) {
      LOG.warn("Failed to parse environment variable {}", name, e);
      return Optional.empty();
    }
  }

  public Optional<List<String>> getList(final String name) {
    return get(name).map(v -> v.split(",")).map(Arrays::asList).map(StringUtil.LIST_SANITIZER);
  }
}
