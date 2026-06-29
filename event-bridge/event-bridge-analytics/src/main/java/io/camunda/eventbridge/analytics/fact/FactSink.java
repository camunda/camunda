/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

/**
 * The publish side of the fact stream — where Stage 1 hands derived facts to Stage 2. Backed by an
 * Event Bridge topic in production, or kept in memory for tests. {@code publish} returns only once
 * the fact is durably accepted, so Stage 1 advances its source position no further than acked
 * facts.
 */
public interface FactSink {

  void publish(ProcessInstanceExecutionTimeFact fact);
}
