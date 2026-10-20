/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Settings by name, as system properties such as {@code eventplane.store.type}, or else as
 * environment variables with the name in upper case and its dots as underscores, such as {@code
 * EVENTPLANE_STORE_TYPE}. A blank value counts as unset.
 */
final class Settings {

  private final Function<String, @Nullable String> lookup;

  Settings(final Function<String, @Nullable String> lookup) {
    this.lookup = lookup;
  }

  /** The system properties, and then the environment. */
  static Settings system() {
    return new Settings(
        name -> {
          final var property = System.getProperty(name);
          return property != null ? property : System.getenv(environmentName(name));
        });
  }

  static String environmentName(final String name) {
    return name.toUpperCase(Locale.ROOT).replace('.', '_');
  }

  /** The value, or null if the setting is unset. */
  @Nullable String optional(final String name) {
    final var value = lookup.apply(name);
    return value == null || value.isBlank() ? null : value.strip();
  }

  /**
   * The value of a setting that must be set.
   *
   * @throws InvalidMaintenanceConfigException if it is unset
   */
  String required(final String name) {
    final var value = optional(name);
    if (value == null) {
      throw new InvalidMaintenanceConfigException(
          name, "to be set, as a system property or as " + environmentName(name), "none");
    }
    return value;
  }

  /**
   * A duration in ISO-8601 form, such as {@code PT24H}.
   *
   * @throws InvalidMaintenanceConfigException if it isn't one
   */
  @Nullable Duration duration(final String name) {
    final var value = optional(name);
    try {
      return value == null ? null : Duration.parse(value);
    } catch (final DateTimeParseException e) {
      throw new InvalidMaintenanceConfigException(
          name, "to be an ISO-8601 duration such as PT24H", "'" + value + "'");
    }
  }

  /**
   * A whole number.
   *
   * @throws InvalidMaintenanceConfigException if it isn't one
   */
  @Nullable Long number(final String name) {
    final var value = optional(name);
    try {
      return value == null ? null : Long.valueOf(value);
    } catch (final NumberFormatException e) {
      throw new InvalidMaintenanceConfigException(name, "to be a whole number", "'" + value + "'");
    }
  }

  /**
   * {@code true} or {@code false}, and false if unset.
   *
   * @throws InvalidMaintenanceConfigException if it is something else
   */
  boolean flag(final String name) {
    final var value = optional(name);
    if (value == null || "false".equalsIgnoreCase(value)) {
      return false;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    throw new InvalidMaintenanceConfigException(name, "to be true or false", "'" + value + "'");
  }
}
