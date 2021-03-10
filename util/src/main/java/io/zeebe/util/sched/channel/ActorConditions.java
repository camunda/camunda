/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.ActorCondition;
import java.util.concurrent.atomic.AtomicReference;

public final class ActorConditions {

  /**
   * For reference see {@link java.nio.Bits#JNI_COPY_TO_ARRAY_THRESHOLD} and {@link
   * java.nio.DirectByteBuffer#get(byte[], int, int)}
   */
  private static final int JNI_COPY_TO_ARRAY_THRESHOLD = 6;

  private static final ActorCondition[] EMPTY_ARRAY = new ActorCondition[0];

  private final AtomicReference<ActorCondition[]> arrayRef = new AtomicReference<>(EMPTY_ARRAY);

  public void registerConsumer(final ActorCondition item) {
    if (null == item) {
      throw new NullPointerException("null items are not supported in this collection");
    }

    ActorCondition[] oldArray;
    ActorCondition[] newArray;

    do {
      oldArray = arrayRef.get();
      final int oldLength = oldArray.length;
      newArray = new ActorCondition[oldLength + 1];

      copyArray(oldArray, 0, newArray, 0, oldLength);

      newArray[oldLength] = item;
    } while (!arrayRef.compareAndSet(oldArray, newArray));
  }

  public void removeConsumer(final ActorCondition item) {
    if (null == item) {
      throw new NullPointerException("null items are not supported in this collection");
    }

    ActorCondition[] oldArray;
    ActorCondition[] newArray;

    do {
      oldArray = arrayRef.get();

      final int index = find(oldArray, item);
      if (-1 == index) {
        return;
      }

      final int newLength = oldArray.length - 1;
      newArray = new ActorCondition[newLength];

      copyArray(oldArray, 0, newArray, 0, index);
      copyArray(oldArray, index + 1, newArray, index, newLength - index);
    } while (!arrayRef.compareAndSet(oldArray, newArray));
  }

  public void signalConsumers() {
    // please do not remove me, array ref may be replaced concurrently
    final ActorCondition[] consumer = arrayRef.get();

    for (int i = 0; i < consumer.length; i++) {
      consumer[i].signal();
    }
  }

  private static int find(final ActorCondition[] array, final ActorCondition condition) {
    for (int i = 0; i < array.length; i++) {
      if (condition.equals(array[i])) {
        return i;
      }
    }

    return -1;
  }

  private static void copyArray(
      final ActorCondition[] src,
      final int srcPos,
      final ActorCondition[] dest,
      final int destPos,
      final int length) {
    if (length < JNI_COPY_TO_ARRAY_THRESHOLD) {
      int srcIndex = srcPos;
      int destIndex = destPos;
      final int endIndex = destPos + length;
      for (; destIndex < endIndex; srcIndex++, destIndex++) {
        dest[destIndex] = src[srcIndex];
      }
    } else {
      System.arraycopy(src, srcPos, dest, destPos, length);
    }
  }
}
