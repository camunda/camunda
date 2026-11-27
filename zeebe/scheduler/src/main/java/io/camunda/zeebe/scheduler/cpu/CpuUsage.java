/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.cpu;

/**
 * The CPU accounting of a thread group for its last sample, in nanoseconds.
 *
 * @param usedNanos CPU time that the threads of the group used since the previous sample
 * @param budgetNanos time since the previous sample times the CPU quota of the group
 * @param debtNanos CPU time above the budget, carried into the next samples
 */
public record CpuUsage(long usedNanos, long budgetNanos, long debtNanos) {}
