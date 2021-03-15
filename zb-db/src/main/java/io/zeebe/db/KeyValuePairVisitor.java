/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

/**
 * Represents an function that accepts a zeebe key value pair and produces an primitive boolean as
 * result.
 *
 * @param <KeyType> the type of the key
 * @param <ValueType> the type of the value
 */
@FunctionalInterface
public interface KeyValuePairVisitor<KeyType extends DbKey, ValueType extends DbValue> {

  /**
   * Visits the zeebe key value pair. The result indicates whether it should visit more key-value
   * pairs or not.
   *
   * @param key the key
   * @param value the value
   * @return true if the visiting should continue, false otherwise
   */
  boolean visit(KeyType key, ValueType value);
}
