/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

/**
 * Marker for the facts the single base-projection fold derives from the process-execution record
 * stream (process-instance execution time, element execution). The fold emits this common type; the
 * runtime fans each fact out to the rollup(s) for its concrete type.
 */
public interface ProcessExecutionFact {}
