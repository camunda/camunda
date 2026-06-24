/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property;

import java.util.Set;

/**
 * Base interface for typed resource properties used in property-based authorization.
 *
 * <p>Each resource type that supports property-based authorization should have a corresponding
 * implementation of this interface.
 *
 * <p>Implementations should be immutable records or classes with defensive copies of mutable
 * fields.
 */
public interface ResourceAuthorizationProperties {

  /**
   * Checks if this properties object has at least one non-null/non-empty property set.
   *
   * @return true if any property is set, false otherwise
   */
  default boolean hasProperties() {
    return !getPropertyNames().isEmpty();
  }

  /**
   * Returns the names of properties that are set (non-null/non-empty).
   *
   * <p>These names correspond to authorization scope property names and are used for:
   *
   * <ul>
   *   <li>Matching against configured authorization scopes
   *   <li>Constructing error messages when authorization fails
   * </ul>
   *
   * @return set of property names that have values set
   */
  Set<String> getPropertyNames();
}
