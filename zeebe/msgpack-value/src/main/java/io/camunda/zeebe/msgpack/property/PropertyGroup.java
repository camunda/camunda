/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.ObjectValue;

/**
 * A set of related properties that are declared together on the owning {@link ObjectValue}. The
 * properties are written as sibling keys of the owner, so a group adds no nesting to the encoding.
 *
 * @see ObjectValue#declareProperties(PropertyGroup)
 */
public interface PropertyGroup {

  /**
   * @return the number of properties {@link #declareOn(ObjectValue)} declares; used to size the
   *     owner's property list.
   */
  int propertyCount();

  /** Declares every property of this group on the given owner, in the order they are written. */
  void declareOn(ObjectValue owner);
}
