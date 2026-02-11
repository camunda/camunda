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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EventDescription {
  private long position;
  private Intent intent;
  private ValueType valueType;
  private String status;
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public EventDescription(final String status) {
    status(status);
  }

  public void status(final String status) {
    rwLock.writeLock().lock();
    // this cannot fail, try/finally is not needed
    this.status = status;
    position = 0;
    intent = null;
    valueType = null;
    rwLock.writeLock().unlock();
  }

  public void set(
      final String status, final long position, final Intent intent, final ValueType valueType) {
    // this cannot fail, try/finally is not needed
    rwLock.writeLock().lock();
    this.status = status;
    this.position = position;
    this.intent = intent;
    this.valueType = valueType;
    rwLock.writeLock().unlock();
  }

  @Override
  public String toString() {
    rwLock.readLock().lock();
    try {
      if (position == 0) {
        return status;
      } else {
        return String.format(
            "%s @ position=%d, intent=%s, valueType=%s", status, position, intent, valueType);
      }
    } finally {
      rwLock.readLock().unlock();
    }
  }
}
