/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.util.exception.UnrecoverableException;

public interface KeyValidator {
  /**
   * Validates that a key is valid and can be written to the log.
   *
   * <ul>
   *   <li>it's assigned to the expected partition
   *   <li>it's not higher than the current key
   * </ul>
   *
   * @param key to append to the log
   * @throws UnrecoverableException if the key is not valid
   */
  void validateKey(final long key);
}
