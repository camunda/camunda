/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl;

import io.zeebe.db.DbKey;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DbCompositeKey<FirstKeyType extends DbKey, SecondKeyType extends DbKey>
    implements DbKey {

  private final FirstKeyType firstKeyTypePart;
  private final SecondKeyType secondKeyTypePart;

  public DbCompositeKey(FirstKeyType firstKeyTypePart, SecondKeyType secondKeyTypePart) {
    this.firstKeyTypePart = firstKeyTypePart;
    this.secondKeyTypePart = secondKeyTypePart;
  }

  public FirstKeyType getFirst() {
    return firstKeyTypePart;
  }

  public SecondKeyType getSecond() {
    return secondKeyTypePart;
  }

  @Override
  public void wrap(DirectBuffer directBuffer, int offset, int length) {
    firstKeyTypePart.wrap(directBuffer, offset, length);
    final int firstKeyLength = firstKeyTypePart.getLength();
    secondKeyTypePart.wrap(directBuffer, offset + firstKeyLength, length - firstKeyLength);
  }

  @Override
  public int getLength() {
    return firstKeyTypePart.getLength() + secondKeyTypePart.getLength();
  }

  @Override
  public void write(MutableDirectBuffer mutableDirectBuffer, int offset) {
    firstKeyTypePart.write(mutableDirectBuffer, offset);
    final int firstKeyPartLength = firstKeyTypePart.getLength();
    secondKeyTypePart.write(mutableDirectBuffer, offset + firstKeyPartLength);
  }
}
