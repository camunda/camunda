/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public record DbCompositeKey<FirstKeyType extends DbKey, SecondKeyType extends DbKey>(
    FirstKeyType first, SecondKeyType second) implements DbKey {

  @Override
  public void wrap(final DirectBuffer directBuffer, final int offset, final int length) {
    first.wrap(directBuffer, offset, length);
    final int firstKeyLength = first.getLength();
    second.wrap(directBuffer, offset + firstKeyLength, length - firstKeyLength);
  }

  @Override
  public int getLength() {
    return first.getLength() + second.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer mutableDirectBuffer, final int offset) {
    first.write(mutableDirectBuffer, offset);
    final int firstKeyPartLength = first.getLength();
    second.write(mutableDirectBuffer, offset + firstKeyPartLength);
  }
}
