/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Represents a function that accepts a key and produces a primitive boolean as result, indicating
 * whether iteration should continue. Unlike {@link KeyValuePairVisitor}, this visitor does not
 * receive the value, which allows implementations to skip reading the value from the database
 * entirely.
 *
 * @param <KeyType> the type of the key
 */
@FunctionalInterface
public interface KeyVisitor<KeyType extends DbKey> {

  /**
   * Visits the key. The result indicates whether it should visit more keys or not.
   *
   * @param key the key
   * @return true if the visiting should continue, false otherwise
   */
  boolean visit(KeyType key);
}
