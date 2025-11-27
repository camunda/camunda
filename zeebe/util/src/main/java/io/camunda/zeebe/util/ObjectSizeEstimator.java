/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;

/**
 * Utility class for estimating the memory footprint of objects using Kryo serialization. This
 * provides accurate size estimates to help prevent OOM conditions.
 */
public class ObjectSizeEstimator {

  private static final ThreadLocal<Kryo> THREAD_LOCAL_KRYO =
      ThreadLocal.withInitial(
          () -> {
            final Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            kryo.setReferences(false);
            return kryo;
          });

  /**
   * Estimates the size of an object in bytes using Kryo serialization.
   *
   * @param object the object to estimate
   * @return estimated size in bytes
   */
  public static long estimateSize(final Object object) {
    if (object == null) {
      return 0;
    }

    final Kryo kryo = THREAD_LOCAL_KRYO.get();

    try (final CountingOutputStream countingStream =
            new CountingOutputStream(NullOutputStream.INSTANCE);
        final Output output = new Output(countingStream, 8192)) { // 8KB buffer

      kryo.writeClassAndObject(output, object);

      // Reset Kryo to clear internal references after large objects
      output.flush();
      kryo.reset();

      return countingStream.getByteCount();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to estimate object size", e);
    }
  }
}
