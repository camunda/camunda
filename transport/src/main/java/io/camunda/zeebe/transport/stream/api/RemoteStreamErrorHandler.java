/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

/**
 * Allows consumers of this API to specify error handling logic when a payload cannot be pushed out.
 *
 * @param <P> the payload type
 */
@FunctionalInterface
public interface RemoteStreamErrorHandler<P> {

  /**
   * This method is called whenever pushing the given {@code data} to a stream has failed.
   *
   * @param error the associated failure
   * @param data the data we attempted to push
   */
  void handleError(final Throwable error, P data);
}
