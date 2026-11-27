/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

/**
 * Notified of changes to a {@link GlobalKey}.
 *
 * @param <T> the type of the value
 */
@FunctionalInterface
public interface GlobalListener<T> {

  /** Called with the current value, which is the key's default value until one was received. */
  void onChange(T value);
}
