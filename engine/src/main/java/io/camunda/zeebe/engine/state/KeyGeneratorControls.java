/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state;

/** Allows to manipulate the key generator. Should be used with caution. */
public interface KeyGeneratorControls extends KeyGenerator {

  /**
   * Set the given value as the new key if it is higher than the current key.
   *
   * @param key the new key
   */
  void setKeyIfHigher(long key);
}
