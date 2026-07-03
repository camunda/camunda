/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Computes a deterministic SHA-256 hash over:
 *
 * <ol>
 *   <li>The (ValueType, Intent, handler class name, handler bytecode) of every registered handler,
 *       sorted by "valueType:intent" to eliminate registration-order variance. The bytecode is
 *       obtained via {@link AnalyticsHandler#digestInput()}, which each handler supplies.
 *   <li>The exporter version string — a release backstop that catches changes to helpers or
 *       constants called from a handler whose own bytecode did not change.
 *   <li>The behavior-affecting configuration fields (see {@link
 *       AnalyticsExporterConfig#toExporterDigestString()}).
 *   <li>The bytecode of {@link AnalyticsAttributes} and all its nested classes — detects renames of
 *       attribute key strings, which don't show up in handler bytecodes.
 * </ol>
 *
 * <p><b>Constraint:</b> handler implementations must be named, top-level or static nested classes.
 * Lambda rejection now lives in {@link AnalyticsHandler#digestInput()}.
 */
final class AnalyticsExporterDigest {

  private static final byte[] SEPARATOR = "|".getBytes(StandardCharsets.UTF_8);

  private AnalyticsExporterDigest() {}

  /**
   * Returns a 64-character lowercase hex SHA-256 string. Throws {@link IllegalStateException} if
   * class bytes cannot be located, or {@link UncheckedIOException} if they cannot be read.
   */
  static String compute(final HandlerRegistry registry, final AnalyticsExporterConfig config) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");

      registry.registeredHandlers().entrySet().stream()
          .flatMap(
              e ->
                  e.getValue().entrySet().stream()
                      .map(
                          ie ->
                              Map.entry(
                                  e.getKey().name() + ":" + ie.getKey().name(), ie.getValue())))
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              e -> {
                digest.update((e.getKey() + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(e.getValue().getClass().getName().getBytes(StandardCharsets.UTF_8));
                digest.update(SEPARATOR);
                digest.update(e.getValue().digestInput());
                digest.update(SEPARATOR);
              });

      // Version backstop: catches changes to helpers/constants not reflected in handler bytecodes.
      digest.update(AnalyticsExporterVersion.get().getBytes(StandardCharsets.UTF_8));
      digest.update(SEPARATOR);

      digest.update(config.toExporterDigestString().getBytes(StandardCharsets.UTF_8));
      digest.update(SEPARATOR);

      // Hash AnalyticsAttributes (outer class + all nested classes, sorted for stability).
      // This detects renames of attribute key strings, which don't show up in handler bytecodes.
      Stream.concat(
              Stream.of(AnalyticsAttributes.class),
              Arrays.stream(AnalyticsAttributes.class.getDeclaredClasses()))
          .sorted(Comparator.comparing(Class::getName))
          .forEach(
              c -> {
                digest.update(c.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(SEPARATOR);
                digest.update(loadBytes(c));
                digest.update(SEPARATOR);
              });

      return HexFormat.of().formatHex(digest.digest());
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM does not support SHA-256", e);
    }
  }

  private static byte[] loadBytes(final Class<?> clazz) {
    final var resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
    try (final var stream = clazz.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Cannot load class bytes for: " + clazz.getName());
      }
      return stream.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read class bytes for: " + clazz.getName(), e);
    }
  }
}
