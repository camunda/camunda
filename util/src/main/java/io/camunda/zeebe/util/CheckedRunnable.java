/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

/** A simple extension of runnable which allows for exceptions to be thrown. */
@FunctionalInterface
// ignore generic exception warning here as we want to allow for any exception to be thrown
@SuppressWarnings("java:S112")
public interface CheckedRunnable {
  void run() throws Exception;
}
