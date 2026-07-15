/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One currently-open process instance from the open-instances working-set table: when it started
 * ({@code startedAt}, the activation event time) and how old it is right now ({@code ageMs},
 * computed server-side as now − startedAt so every row of one response shares the same "now").
 */
public record OpenInstanceRow(
    long processInstanceKey, String bpmnProcessId, long startedAt, long ageMs) {}
