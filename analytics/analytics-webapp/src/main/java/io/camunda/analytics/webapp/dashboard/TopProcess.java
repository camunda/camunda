/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One heavy hitter from the top-processes rollup: its rank (1-based), the process id, and the
 * estimated occurrence count with the sketch's guaranteed bounds.
 */
public record TopProcess(
    int rank, String bpmnProcessId, long estimate, long lowerBound, long upperBound) {}
