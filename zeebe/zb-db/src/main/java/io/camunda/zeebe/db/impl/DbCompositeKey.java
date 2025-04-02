/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DbCompositeKey<FirstKeyType extends DbKey, SecondKeyType extends DbKey>
    implements DbKey, DbValue, ContainsForeignKeys {
  final FirstKeyType first;
  final SecondKeyType second;
  final Collection<DbForeignKey<DbKey>> containedForeignKeys;

  public DbCompositeKey(final FirstKeyType first, final SecondKeyType second) {
    this.first = first;
    this.second = second;
    containedForeignKeys = collectContainedForeignKeys(first, second);
  }

  public FirstKeyType first() {
    return first;
  }

  public SecondKeyType second() {
    return second;
  }

  @Override
  public Collection<DbForeignKey<DbKey>> containedForeignKeys() {
    return containedForeignKeys;
  }

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

  private static Collection<DbForeignKey<DbKey>> collectContainedForeignKeys(
      final DbKey first, final DbKey second) {
    final var result = new ArrayList<DbForeignKey<DbKey>>();
    if (first instanceof final ContainsForeignKeys firstForeignKeyProvider) {
      result.addAll(firstForeignKeyProvider.containedForeignKeys());
    }
    if (second instanceof final ContainsForeignKeys secondForeignKeyProvider) {
      result.addAll(secondForeignKeyProvider.containedForeignKeys());
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public String toString() {
    return "DbCompositeKey{" + "first=" + first + ", second=" + second + '}';
  }
}
