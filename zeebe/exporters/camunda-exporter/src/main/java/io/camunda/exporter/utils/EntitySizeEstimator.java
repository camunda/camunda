/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import io.camunda.webapps.schema.entities.ExporterEntity;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;

public class EntitySizeEstimator {

  private static final ThreadLocal<Kryo> THREAD_LOCAL_KRYO =
      ThreadLocal.withInitial(
          () -> {
            final Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            kryo.setReferences(false);
            return kryo;
          });

  public static long estimateEntitySize(final ExporterEntity<?> entity) {
    final Kryo kryo = THREAD_LOCAL_KRYO.get();

    try (final CountingOutputStream countingStream =
            new CountingOutputStream(NullOutputStream.INSTANCE);
        final Output output = new Output(countingStream, 8192)) { // 8KB buffer

      kryo.writeObject(output, entity);

      // Optionally reset Kryo to clear internal references after large objects
      kryo.reset();

      return countingStream.getByteCount();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to estimate object size", e);
    }
  }
}
