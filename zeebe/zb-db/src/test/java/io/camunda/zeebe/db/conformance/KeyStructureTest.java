/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.KeyStructure;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Runs the key structure conformance tests against the key types of this module, independent of any
 * storage backend.
 */
final class KeyStructureTest extends AbstractKeyStructureTest {

  @Override
  protected DbKey emptyKey() {
    return new DbKey() {
      @Override
      public void describe(final KeyStructure structure) {}

      @Override
      public void wrap(final DirectBuffer buffer, final int offset, final int length) {}

      @Override
      public int getLength() {
        return 0;
      }

      @Override
      public int write(final MutableDirectBuffer buffer, final int offset) {
        return 0;
      }
    };
  }
}
