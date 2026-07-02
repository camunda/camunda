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
import java.util.Comparator;
import java.util.HexFormat;

/**
 * Computes a deterministic SHA-256 hash over:
 *
 * <ol>
 *   <li>The (ValueType, Intent, handler class name, handler bytecode) of every registered handler,
 *       sorted by "valueType:intent" to eliminate registration-order variance.
 *   <li>The exporter version string — a release backstop that catches changes to helpers or
 *       constants called from a handler whose own bytecode did not change (e.g. renaming an event
 *       name constant in {@link AnalyticsAttributes}).
 *   <li>The behavior-affecting configuration fields (see {@link
 *       AnalyticsExporterConfig#toBehaviorString()}).
 * </ol>
 *
 * <p><b>Constraint:</b> handler implementations must be named, top-level or static nested classes.
 * Lambdas and anonymous classes are rejected at compute time because their JVM-generated names are
 * non-deterministic across restarts and they have no backing {@code .class} resource on the
 * classpath.
 *
 * <p><b>Limitation:</b> Java compiles each class into its own {@code .class} file. When a handler
 * reads a constant from another class (e.g. {@link AnalyticsAttributes.Event}), the handler's file
 * stores only a <em>symbolic reference</em> — the name of the field — not the value the field
 * holds. So if you change the <em>value</em> of a constant (e.g. rename the OTel attribute key
 * string from {@code "camunda.event.name"} to {@code "event.name"}), the handler's {@code .class}
 * file is byte-for-byte identical and the hash does not change. Note: renaming the Java field
 * itself (e.g. {@code NAME} → {@code EVENT_NAME}) <em>is</em> detected because the symbolic
 * reference in the bytecode changes. The version string (step 2) is the safety net: bump {@link
 * AnalyticsExporterVersion} in any release that ships a behavioral change in a shared class, even
 * if no handler file was edited.
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

      registry
          .handlerEntries()
          .sorted(Comparator.comparing(e -> e.valueType().name() + ":" + e.intent().name()))
          .forEach(
              e -> {
                digest.update(
                    (e.valueType().name() + ":" + e.intent().name() + ":")
                        .getBytes(StandardCharsets.UTF_8));
                digest.update(e.handlerClass().getName().getBytes(StandardCharsets.UTF_8));
                digest.update(SEPARATOR);
                digest.update(loadClassBytes(e.handlerClass()));
                digest.update(SEPARATOR);
              });

      // Version backstop: catches changes to helpers/constants not reflected in handler bytecodes.
      digest.update(AnalyticsExporterVersion.get().getBytes(StandardCharsets.UTF_8));
      digest.update(SEPARATOR);

      digest.update(config.toBehaviorString().getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(digest.digest());
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM does not support SHA-256", e);
    }
  }

  private static byte[] loadClassBytes(final Class<?> clazz) {
    if (clazz.isSynthetic() || clazz.isAnonymousClass() || clazz.isLocalClass()) {
      throw new IllegalArgumentException(
          "Handler class must be a named class — lambdas, anonymous classes, and local classes"
              + " are not supported because their bytecode is not stable across JVM restarts: "
              + clazz.getName());
    }
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
