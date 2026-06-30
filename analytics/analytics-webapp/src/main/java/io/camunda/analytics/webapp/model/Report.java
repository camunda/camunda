/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.model;

/**
 * A report on top of a {@link Dataset}: a visualization plus optional filters (a specific process,
 * and an event-time window range). Null filter fields mean "no filter".
 */
public record Report(
    long id,
    String name,
    long datasetId,
    String vizType,
    String bpmnProcessId,
    Long fromWindow,
    Long toWindow) {}
