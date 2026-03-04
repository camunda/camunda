/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventDescription {
  private static final int WRITE_ACQUIRE_LOCK_TIMEOUT = 1;
  private static final int READ_ACQUIRE_LOCK_TIMEOUT = 100;
  private static final String FAILED_TO_ACQUIRE_LOCK = "failed to acquire lock";
  private long position;
  private Intent intent;
  private ValueType valueType;
  private String status;
  private final Lock lock = new ReentrantLock();

  public EventDescription(final String status) {
    status(status);
  }

  public void status(final String status) {
    try {
      if (lock.tryLock(WRITE_ACQUIRE_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
        try {
          this.status = status;
          position = 0;
          intent = null;
          valueType = null;
        } finally {
          lock.unlock();
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void set(
      final String status, final long position, final Intent intent, final ValueType valueType) {
    try {
      if (lock.tryLock(WRITE_ACQUIRE_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
        try {
          this.status = status;
          this.position = position;
          this.intent = intent;
          this.valueType = valueType;
        } finally {
          lock.unlock();
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public String toString() {
    try {
      if (lock.tryLock(READ_ACQUIRE_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
        try {
          if (position == 0) {
            return status;
          } else {
            return String.format(
                "%s @ position=%d, intent=%s, valueType=%s", status, position, intent, valueType);
          }
        } finally {
          lock.unlock();
        }
      } else {
        return FAILED_TO_ACQUIRE_LOCK;
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return FAILED_TO_ACQUIRE_LOCK;
    }
  }
}
