/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

/** The key which is used to store a value. */
public interface DbKey extends BufferReader, BufferWriter {

  /**
   * Implementation of DbKey can define certain key ranges or structure, if this format doesn't
   * apply to the current key instance, then this method should return false.
   *
   * <p>In this case the database can do some shortcuts like not storing the data or returning false
   * immediately.
   *
   * @return true, if applies to a specific key format
   */
  boolean isValid();
}
