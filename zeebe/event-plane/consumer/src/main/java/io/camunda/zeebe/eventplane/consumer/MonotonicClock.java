/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

/**
 * This process's own monotonic clock, in nanoseconds from an arbitrary origin. Workers time
 * liveness and holes on it, and never compare it with times that others wrote.
 */
@FunctionalInterface
interface MonotonicClock {

  MonotonicClock SYSTEM = System::nanoTime;

  long nanoTime();
}
