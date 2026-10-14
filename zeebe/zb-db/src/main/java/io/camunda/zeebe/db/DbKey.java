/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

/** The key which is used to store a value. */
public interface DbKey extends BufferReader, BufferWriter {

  /**
   * Declares the structure of this key to the given {@link KeyStructure}: its components in the
   * exact order and with the exact widths that {@link #write} serializes them.
   *
   * <p>Keys made of other keys recurse into their parts, so a composite key describes as the flat
   * sequence of all its leaf components.
   */
  void describe(KeyStructure structure);
}
