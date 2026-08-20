/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

/**
 * Marks a type as a projection of another type.
 *
 * <p>Projections intentionally map one type into a different target type, often storing only a
 * subset or a reshaped view of the source data.
 *
 * @param <From> the projected source type
 * @param <To> the receiving projection type
 */
public interface ProjectionOf<From, To> {

  /**
   * Projects the given source into this target instance.
   *
   * <p>Implementations may copy only the fields relevant for the projected representation.
   */
  To wrap(From source);
}
