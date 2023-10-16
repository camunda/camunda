/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.value;

import java.util.Iterator;

public interface MutableArrayValueIterator<T extends BaseValue> extends Iterator<T> {

  /**
   * Write any changes made to the value to the underlying buffer. This must be called when
   * modifying a value during iteration. If not, the changes are not written to the buffer!
   */
  void flush();
}
