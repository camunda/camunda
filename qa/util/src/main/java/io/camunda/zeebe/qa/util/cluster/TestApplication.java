/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

public interface TestApplication<T extends TestApplication<T>> extends AutoCloseable {

  /** Starts the node in a blocking fashion. */
  T start();

  /** Attempts to stop the container gracefully in a blocking fashion. */
  T stop();

  /** Returns whether the underlying application is started yet; does not include any probes */
  boolean isStarted();

  @Override
  default void close() {
    //noinspection resource
    stop();
  }

  /** Convenience method to return the appropriate concrete type */
  T self();

  /**
   * When the underlying application is started, all beans of the given type will resolve to the
   * given value. The qualifier is useful for cases where more than one beans of the same type are
   * defined with different qualifiers.
   *
   * @param qualifier the bean name/qualifier
   * @param bean the object to inject as the bean value
   * @param type the type to be resolved/autowired
   * @return itself for chaining
   * @param <V> the bean type
   */
  <V> T withBean(final String qualifier, final V bean, final Class<V> type);
}
