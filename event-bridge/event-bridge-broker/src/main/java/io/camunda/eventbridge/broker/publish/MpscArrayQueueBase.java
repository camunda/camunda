/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/*
 * Base class: shared immutable fields. Safe to share cache lines since they're never written
 * after construction.
 */
class MpscArrayQueueBase<E> {

  static final VarHandle TAIL_HANDLE;
  static final VarHandle ARRAY_HANDLE;

  static {
    try {
      TAIL_HANDLE =
          MethodHandles.lookup()
              .findVarHandle(MpscArrayQueueProducerField.class, "tail", long.class);
      // Create a VarHandle for direct array element access
      ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    } catch (final ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // Use a raw array instead of AtomicReferenceArray
  final E[] buffer;
  final int mask;

  @SuppressWarnings("unchecked")
  MpscArrayQueueBase(final int capacity) {
    // Array size must be a power of two for the mask to work
    buffer = (E[]) new Object[capacity];
    mask = capacity - 1;
  }
}

/*
 * Padding between base fields and producer fields.
 */
class MpscArrayQueueBaseToProducerPad<E> extends MpscArrayQueueBase<E> {
  @SuppressWarnings("unused")
  long p00, p01, p02, p03, p04, p05, p06, p07;

  MpscArrayQueueBaseToProducerPad(final int capacity) {
    super(capacity);
  }
}

/*
 * Producer-side fields: tail (shared CAS target) and producerLimit (cached head for producers).
 * On the same cache line — producers read both, good locality.
 */
class MpscArrayQueueProducerField<E> extends MpscArrayQueueBaseToProducerPad<E> {
  volatile long tail;
  volatile long producerLimit;

  MpscArrayQueueProducerField(final int capacity) {
    super(capacity);
  }
}

/*
 * Padding between producer fields and consumer fields.
 */
class MpscArrayQueueProducerToConsumerPad<E> extends MpscArrayQueueProducerField<E> {
  @SuppressWarnings("unused")
  long p10, p11, p12, p13, p14, p15, p16, p17;

  MpscArrayQueueProducerToConsumerPad(final int capacity) {
    super(capacity);
  }
}

/*
 * Consumer-side fields: head (single-writer) and cachedTail (consumer-local cache).
 * On the same cache line — consumer reads both, good locality.
 *
 * head is volatile: producers read it (rarely, via producerLimit refresh) and the consumer
 * writes it. Volatile ensures the producer eventually sees the consumer's updates.
 */
class MpscArrayQueueConsumerField<E> extends MpscArrayQueueProducerToConsumerPad<E> {
  volatile long head;
  long cachedTail;

  MpscArrayQueueConsumerField(final int capacity) {
    super(capacity);
  }
}
