/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Detectors that turn signals the broker already has (metrics, listeners) into nodes of the
 * broker's health tree. A detector reports {@link
 * io.camunda.zeebe.util.health.HealthStatus#DEGRADED} while its condition holds, so the condition
 * shows in the health tree and its history without changing readiness, the topology or the
 * partition health that clients see.
 *
 * <p>Prototype: thresholds and the set of detectors are expected to change.
 */
@NullMarked
package io.camunda.zeebe.broker.system.monitoring.detector;

import org.jspecify.annotations.NullMarked;
