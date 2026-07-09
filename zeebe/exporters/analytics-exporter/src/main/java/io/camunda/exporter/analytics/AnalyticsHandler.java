/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;

/**
 * Handler for a single analytics event type. Implementations must be named classes (not lambdas or
 * anonymous classes) so that {@link AnalyticsExporterDigest} can load and hash their bytecode
 * deterministically.
 */
public interface AnalyticsHandler<T extends RecordValue> {

  void handle(Record<T> record);

  /**
   * Returns the bytecode of this handler's class, used by {@link AnalyticsExporterDigest} to
   * compute a digest that detects handler implementation changes. Override to include additional
   * identity (e.g. constructor parameters) in the digest.
   *
   * <p>Lambdas, anonymous classes, and local classes are rejected because they have no stable
   * {@code .class} resource on the classpath.
   */
  default byte[] digestInput() {
    final var clazz = getClass();
    if (clazz.isSynthetic() || clazz.isAnonymousClass() || clazz.isLocalClass()) {
      throw new IllegalArgumentException(
          "Handler class must be a named class — lambdas, anonymous classes, and local classes"
              + " are not supported because their bytecode is not stable across JVM restarts: "
              + clazz.getName());
    }
    return AnalyticsExporterDigest.loadClassBytes(clazz);
  }
}
